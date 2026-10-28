package io.trellis.app.sdk;

import io.trellis.common.dto.CaptureDataDto;
import io.trellis.common.dto.WorkflowInstanceTaskDto;
import io.trellis.common.entity.mongo.CaptureTaskDataAuditing;
import io.trellis.common.entity.mongo.CapturedTask;
import io.trellis.common.entity.mongo.CapturedTaskData;
import io.trellis.common.entity.mongo.WorkFlow;
import io.trellis.common.entity.mongo.WorkFlowTask;
import io.trellis.common.enums.TaskStatus;
import io.trellis.common.enums.WorkflowCaptureTaskStatus;
import io.trellis.common.enums.WorkflowStatus;
import io.trellis.common.exceptions.WorkflowNotFoundException;
import io.trellis.client.clients.WorkFlowClientService;
import io.trellis.core.taskmanager.TaskLifecycleService;
import io.trellis.db.repository.CaptureDataRepository;
import io.trellis.db.repository.CaptureTaskDataAuditingRepository;
import io.trellis.db.repository.WorkFlowRepository;
import io.trellis.app.config.TrellisRuntimeProperties;
import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.events.WorkflowSubmittedEvent;
import io.trellis.app.exception.TrellisBusinessException;
import io.trellis.app.sdk.dto.SdkCaptureRequest;
import io.trellis.app.sdk.dto.SdkSubmitResponse;
import io.trellis.app.sdk.dto.SdkTaskTreeResponse;
import io.trellis.app.sdk.dto.SdkUpdateResponse;
import io.trellis.app.tasks.TaskStatusResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Backs the /api/v1/sdk/workflow endpoints that rn-trellis-core calls.
 *
 * "workflowId" in every call may be a real workflowId or a workflowCode such as LOAN_APPLICATION.
 * A code resolves to the latest PUBLISHED version the first time an asset starts, and the asset
 * stays on that version afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SdkWorkflowService {

    private static final Set<WorkflowCaptureTaskStatus> LOCKED =
            Set.of(WorkflowCaptureTaskStatus.SUBMITTED, WorkflowCaptureTaskStatus.COMPLETED, WorkflowCaptureTaskStatus.CANCELLED);

    private final WorkFlowClientService workFlowClientService;
    private final WorkFlowRepository workFlowRepository;
    private final CaptureDataRepository captureDataRepository;
    private final CaptureTaskDataAuditingRepository auditingRepository;
    private final TaskLifecycleService<TrellisTaskContext> taskLifecycleService;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final TrellisRuntimeProperties properties;

    // ------------------------------------------------------------------ tree

    public SdkTaskTreeResponse getTaskTree(String tenantId, String assetId, String workflowIdOrCode, String mediaUrl) {
        CapturedTaskData data = getOrStart(tenantId, assetId, workflowIdOrCode);
        return buildTreeResponse(data, mediaUrl);
    }

    public SdkTaskTreeResponse getSnapshot(String tenantId, String assetId, String workflowIdOrCode, int version, String mediaUrl) {
        CapturedTaskData current = getOrStart(tenantId, assetId, workflowIdOrCode);
        if (current.getVersion() != null && current.getVersion() == version) {
            return buildTreeResponse(current, mediaUrl);
        }
        CapturedTaskData old = auditingRepository.findSnapshot(tenantId, assetId, current.getWorkflowId(), version)
                .map(CaptureTaskDataAuditing::getCapturedTaskData)
                .orElseThrow(() -> new TrellisBusinessException("No snapshot with version " + version, HttpStatus.NOT_FOUND));
        SdkTaskTreeResponse response = buildTreeResponse(old, mediaUrl);
        response.setIsEditable(false);
        return response;
    }

    private SdkTaskTreeResponse buildTreeResponse(CapturedTaskData data, String mediaUrl) {
        List<WorkflowInstanceTaskDto> tree = workFlowClientService.getTreeWithCapturedData(data);
        return SdkTaskTreeResponse.builder()
                .assetId(data.getAssetId())
                .workflowId(data.getWorkflowId())
                .workflowCode(data.getWorkflowCode())
                .workflowVersion(data.getWorkflowVersion())
                .workflowExecutionId(data.getId())
                .version(data.getVersion())
                .status(data.getWorkflowCaptureTaskStatus())
                .isEditable(!isLocked(data.getWorkflowCaptureTaskStatus()))
                .mediaUrl(mediaUrl)
                .tasksTree(tree)
                .build();
    }

    // ---------------------------------------------------------------- update

    public SdkUpdateResponse updateTask(String tenantId, SdkCaptureRequest request) {
        if (StringUtils.isAnyBlank(request.getAssetId(), request.getWorkflowId(), request.getTaskId())) {
            throw new TrellisBusinessException("assetId, workflowId and taskId are required");
        }
        CapturedTaskData data = getOrStart(tenantId, request.getAssetId(), request.getWorkflowId());
        if (isLocked(data.getWorkflowCaptureTaskStatus())) {
            throw new TrellisBusinessException("This application is already " + data.getWorkflowCaptureTaskStatus()
                    + " and can no longer be edited", HttpStatus.CONFLICT, request.getTaskId());
        }

        TrellisTaskContext context = new TrellisTaskContext();
        context.setTenantId(tenantId);
        context.setWorkflowId(data.getWorkflowId());
        context.setSourceType(data.getSourceType());
        context.setUpdatedBy(request.getCapturedBy());

        CaptureDataDto dto = CaptureDataDto.builder()
                .taskId(request.getTaskId())
                .assetId(request.getAssetId())
                .workflowId(data.getWorkflowId())
                .capturedBy(request.getCapturedBy())
                .capturedValue(request.getCapturedValue() == null ? new HashMap<>() : request.getCapturedValue())
                .build();
        try {
            context = taskLifecycleService.executeTaskLifecycle(dto, context);
        } catch (RuntimeException ex) {
            TrellisBusinessException business = TrellisBusinessException.findIn(ex);
            if (business != null) {
                log.info("Answer rejected: assetId={} taskId={} reason={}", request.getAssetId(), request.getTaskId(),
                        business.getMessage());
                throw business;
            }
            throw ex;
        }

        if (data.getWorkflowCaptureTaskStatus() == null || data.getWorkflowCaptureTaskStatus() == WorkflowCaptureTaskStatus.NOT_STARTED) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(data.getId())),
                    new Update().set("workflowCaptureTaskStatus", WorkflowCaptureTaskStatus.IN_PROGRESS),
                    CapturedTaskData.class);
        }

        CapturedTask saved = context.getCapturedTaskData().getCapturedTasks().get(request.getTaskId());
        return SdkUpdateResponse.builder()
                .taskId(request.getTaskId())
                .taskStatus(context.getTaskStatus())
                .capturedValue(saved != null ? saved.getCapturedValue() : null)
                .dependentTaskUpdates(context.getDependentTaskUpdates())
                .version(context.getCapturedTaskData().getVersion())
                .build();
    }

    // ---------------------------------------------------------------- submit

    public SdkSubmitResponse submit(String tenantId, String assetId, String workflowIdOrCode, String submittedBy) {
        CapturedTaskData data = getOrStart(tenantId, assetId, workflowIdOrCode);
        if (isLocked(data.getWorkflowCaptureTaskStatus())) {
            return SdkSubmitResponse.builder().success(true).message("Already submitted").version(data.getVersion()).build();
        }
        List<WorkflowInstanceTaskDto> tree = workFlowClientService.getTreeWithCapturedData(data);
        Map<String, WorkFlowTask> blueprint = workFlowClientService.getListOfWorkflowTasks(data.getWorkflowId()).stream()
                .collect(Collectors.toMap(WorkFlowTask::getTaskId, Function.identity(), (a, b) -> a));

        List<WorkflowInstanceTaskDto> incomplete = new ArrayList<>();
        collectIncomplete(tree, blueprint, incomplete);
        if (!incomplete.isEmpty()) {
            log.info("Submit blocked for assetId={}: {} task(s) incomplete {}", assetId, incomplete.size(),
                    incomplete.stream().map(WorkflowInstanceTaskDto::getTaskId).toList());
            return SdkSubmitResponse.builder()
                    .success(false)
                    .message(incomplete.size() + " required task(s) still need an answer")
                    .incompleteTasks(SdkSubmitResponse.IncompleteTasks.builder().childTasks(incomplete).build())
                    .version(data.getVersion())
                    .build();
        }

        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(data.getId())),
                new Update().set("workflowCaptureTaskStatus", WorkflowCaptureTaskStatus.SUBMITTED)
                        .set("additionalData.submittedAt", LocalDateTime.now().toString())
                        .set("additionalData.submittedBy", submittedBy)
                        .inc("docVersion", 1),
                CapturedTaskData.class);
        CapturedTaskData submitted = captureDataRepository.findById(data.getId()).orElse(data);
        eventPublisher.publishEvent(new WorkflowSubmittedEvent(submitted, submittedBy));
        log.info("Workflow submitted: assetId={} workflowCode={} version={}", assetId, data.getWorkflowCode(), data.getVersion());
        return SdkSubmitResponse.builder().success(true).message("Submitted successfully").version(data.getVersion()).build();
    }

    private void collectIncomplete(List<WorkflowInstanceTaskDto> tasks, Map<String, WorkFlowTask> blueprint,
                                   List<WorkflowInstanceTaskDto> out) {
        if (tasks == null) {
            return;
        }
        for (WorkflowInstanceTaskDto task : tasks) {
            if (!task.isActive()) {
                continue; // an inactive section hides everything below it
            }
            WorkFlowTask definition = blueprint.get(task.getTaskId());
            if (definition != null && Boolean.TRUE.equals(definition.getIsSystemInput())) {
                continue; // filled by the system, not by the user
            }
            if (task.getChildTasks() != null && !task.getChildTasks().isEmpty()) {
                collectIncomplete(task.getChildTasks(), blueprint, out);
                continue;
            }
            Map<String, Object> meta = task.getMeta();
            boolean actionable = meta != null && Boolean.TRUE.equals(meta.get("isActionable"));
            if (actionable && TaskStatusResolver.isMandatory(meta) && task.getTaskStatus() != TaskStatus.COMPLETED) {
                out.add(task);
            }
        }
    }

    // ----------------------------------------------------------------- reset

    /** Starts a fresh answer sheet. The old one is copied to captured_task_data_auditing by CapturedTaskDataAuditListener. */
    public SdkTaskTreeResponse reset(String tenantId, String assetId, String workflowIdOrCode, String mediaUrl) {
        CapturedTaskData data = getOrStart(tenantId, assetId, workflowIdOrCode);
        data.setVersion((data.getVersion() == null ? 1 : data.getVersion()) + 1);
        data.setCapturedTasks(defaultCapturedTasks(data.getWorkflowId()));
        data.setAdditionalData(new HashMap<>());
        data.setWorkflowCaptureTaskStatus(WorkflowCaptureTaskStatus.NOT_STARTED);
        CapturedTaskData saved = captureDataRepository.save(data);
        return buildTreeResponse(saved, mediaUrl);
    }

    // --------------------------------------------------------------- helpers

    private static boolean isLocked(WorkflowCaptureTaskStatus status) {
        return status != null && LOCKED.contains(status);
    }

    /** Finds the asset's answer sheet, or creates one on the latest published workflow version. */
    public CapturedTaskData getOrStart(String tenantId, String assetId, String workflowIdOrCode) {
        if (StringUtils.isAnyBlank(assetId, workflowIdOrCode)) {
            throw new TrellisBusinessException("assetId and workflowId are required");
        }
        Optional<WorkFlow> byId = workFlowRepository.findById(workflowIdOrCode);
        if (byId.isPresent()) {
            return captureDataRepository.findByTenantIdAndWorkflowIdAndAssetId(tenantId, byId.get().getWorkflowId(), assetId)
                    .orElseGet(() -> start(tenantId, assetId, byId.get()));
        }
        Optional<CapturedTaskData> existing =
                captureDataRepository.findByTenantIdAndWorkflowCodeAndAssetId(tenantId, workflowIdOrCode, assetId);
        if (existing.isPresent()) {
            return existing.get();
        }
        WorkFlow latest = workFlowRepository
                .findFirstByWorkflowCodeAndWorkflowStatusOrderByWorkflowVersionDesc(workflowIdOrCode, WorkflowStatus.COMPLETED)
                .orElseThrow(() -> new WorkflowNotFoundException("No published workflow with id or code: " + workflowIdOrCode));
        return start(tenantId, assetId, latest);
    }

    private CapturedTaskData start(String tenantId, String assetId, WorkFlow workflow) {
        if (workflow.getWorkflowStatus() != WorkflowStatus.COMPLETED) {
            throw new TrellisBusinessException("Workflow " + workflow.getWorkflowId() + " is not published yet", HttpStatus.CONFLICT);
        }
        CapturedTaskData data = new CapturedTaskData();
        data.setTenantId(tenantId);
        data.setAssetId(assetId);
        data.setWorkflowId(workflow.getWorkflowId());
        data.setWorkflowCode(workflow.getWorkflowCode());
        data.setWorkflowVersion(workflow.getWorkflowVersion());
        data.setVersion(1);
        data.setSourceType(properties.getSdkSourceType());
        data.setWorkflowCaptureTaskStatus(WorkflowCaptureTaskStatus.NOT_STARTED);
        data.setCapturedTasks(defaultCapturedTasks(workflow.getWorkflowId()));
        log.info("Started execution of {} v{} for assetId={} tenant={}", workflow.getWorkflowCode(),
                workflow.getWorkflowVersion(), assetId, tenantId);
        return captureDataRepository.save(data);
    }

    /** Tasks with isActiveByDefault or defaultCapturedValue get an entry up front (inactive ones stay hidden). */
    private Map<String, CapturedTask> defaultCapturedTasks(String workflowId) {
        Map<String, CapturedTask> defaults = new HashMap<>();
        for (WorkFlowTask task : workFlowClientService.getListOfWorkflowTasks(workflowId)) {
            if (task.getIsActiveByDefault() == null && task.getDefaultCapturedValue() == null) {
                continue;
            }
            CapturedTask captured = new CapturedTask();
            captured.setActive(!Boolean.FALSE.equals(task.getIsActiveByDefault()));
            if (task.getDefaultCapturedValue() != null) {
                captured.setCapturedValue(new HashMap<>(task.getDefaultCapturedValue()));
                captured.setTaskStatus(TaskStatusResolver.resolve(task.getMeta(), captured.getCapturedValue()));
            }
            defaults.put(task.getTaskId(), captured);
        }
        return defaults;
    }
}
