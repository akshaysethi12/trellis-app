package io.trellis.app.context;

import io.trellis.common.dto.CaptureDataDto;
import io.trellis.common.entity.mongo.CapturedTask;
import io.trellis.common.entity.mongo.CapturedTaskData;
import io.trellis.common.entity.mongo.WorkFlow;
import io.trellis.common.entity.mongo.WorkFlowTask;
import io.trellis.core.taskmanager.DefaultTaskContextBuilder;
import io.trellis.db.repository.CaptureDataRepository;
import io.trellis.db.repository.WorkFlowRepository;
import io.trellis.db.repository.WorkFlowTaskRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;

/**
 * Fills a {@link TrellisTaskContext} from the incoming answer, the task blueprint and the asset's saved answers.
 * The parent class loads the CapturedTaskData (or creates a fresh one) and the WorkFlowTask.
 */
@Component
public class TrellisTaskContextBuilder extends DefaultTaskContextBuilder<TrellisTaskContext> {

    private final WorkFlowRepository workFlowRepository;

    public TrellisTaskContextBuilder(WorkFlowTaskRepository workFlowTaskRepository,
                                     CaptureDataRepository captureDataRepository,
                                     WorkFlowRepository workFlowRepository) {
        super(workFlowTaskRepository, captureDataRepository);
        this.workFlowRepository = workFlowRepository;
    }

    @Override
    protected TrellisTaskContext buildCustomTaskContext(CaptureDataDto dto, WorkFlowTask workFlowTask,
                                                        CapturedTaskData capturedTaskData, TrellisTaskContext context) {
        context.setTaskId(dto.getTaskId());
        context.setTaskType(workFlowTask.getTaskType());
        context.setAssetId(dto.getAssetId());
        context.setWorkflowId(capturedTaskData.getWorkflowId());
        context.setTenantId(capturedTaskData.getTenantId());
        context.setCapturedValue(dto.getCapturedValue() != null ? new HashMap<>(dto.getCapturedValue()) : new HashMap<>());
        context.setWorkFlowTask(workFlowTask);
        context.setCapturedTaskData(capturedTaskData);
        context.setSchemaVersion(capturedTaskData.getVersion());
        context.setRequestTimestamp(Instant.now());
        if (dto.getCapturedBy() != null) {
            context.setUpdatedBy(dto.getCapturedBy());
        }

        CapturedTask existing = capturedTaskData.getCapturedTasks().get(dto.getTaskId());
        if (existing != null && context.getTaskStatus() == null) {
            context.setTaskStatus(existing.getTaskStatus());
        }

        if (capturedTaskData.getWorkflowCode() == null) {
            workFlowRepository.findById(capturedTaskData.getWorkflowId()).ifPresent((WorkFlow wf) -> {
                capturedTaskData.setWorkflowCode(wf.getWorkflowCode());
                capturedTaskData.setWorkflowVersion(wf.getWorkflowVersion());
            });
        }
        context.setWorkflowCode(capturedTaskData.getWorkflowCode());
        context.setWorkflowVersion(capturedTaskData.getWorkflowVersion());
        return context;
    }
}
