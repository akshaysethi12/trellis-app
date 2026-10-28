package io.trellis.app.seed;

import io.trellis.common.dto.WorkFlowDTO;
import io.trellis.common.entity.mongo.WorkFlow;
import io.trellis.common.entity.mongo.WorkFlowTask;
import io.trellis.common.enums.WorkflowStatus;
import io.trellis.core.services.TaskTypeService;
import io.trellis.core.services.WorkFlowService;
import io.trellis.core.services.WorkFlowTaskService;
import io.trellis.db.repository.WorkFlowRepository;
import io.trellis.app.config.TrellisRuntimeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * On startup, loads task types and workflow blueprints from trellis-seed/*.json using the normal Trellis services:
 *   TaskTypeService.updateTaskTypes      -> task_types
 *   WorkFlowService.createEmptyWorkflow  -> workflows (status CREATED = draft)
 *   WorkFlowTaskService.saveAll          -> workflow_tasks (meta validated against the task type's JSON schema)
 *   WorkFlowService.publishWorkflow      -> status COMPLETED (= published, now usable by clients)
 * A workflowCode that already has a published version is skipped unless trellis.runtime.seed.force-new-version=true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrellisSeedLoader implements ApplicationRunner {

    private final TrellisRuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final TaskTypeService taskTypeService;
    private final WorkFlowService workFlowService;
    private final WorkFlowTaskService workFlowTaskService;
    private final WorkFlowRepository workFlowRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.getSeed().isEnabled()) {
            log.info("Trellis seed loading disabled");
            return;
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String location : properties.getSeed().getLocations()) {
            for (Resource resource : resolver.getResources(location)) {
                try (InputStream in = resource.getInputStream()) {
                    TrellisSeedFile seed = objectMapper.readValue(in, TrellisSeedFile.class);
                    apply(seed, resource.getFilename());
                }
            }
        }
    }

    private WorkFlowTask toTask(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, WorkFlowTask.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid task in seed file: " + node.path("taskId").asText(), e);
        }
    }

    private void apply(TrellisSeedFile seed, String source) {
        if (!seed.getTaskTypes().isEmpty()) {
            taskTypeService.updateTaskTypes(seed.getTaskTypes());
            log.info("[seed:{}] upserted {} task types", source, seed.getTaskTypes().size());
        }
        for (TrellisSeedFile.SeedWorkflow wf : seed.getWorkflows()) {
            Optional<WorkFlow> published = workFlowRepository
                    .findFirstByWorkflowCodeAndWorkflowStatusOrderByWorkflowVersionDesc(wf.getWorkflowCode(), WorkflowStatus.COMPLETED);
            if (published.isPresent() && !properties.getSeed().isForceNewVersion()) {
                log.info("[seed:{}] {} already published as v{} ({}), skipping", source, wf.getWorkflowCode(),
                        published.get().getWorkflowVersion(), published.get().getWorkflowId());
                continue;
            }
            Optional<WorkFlow> latest = workFlowRepository.findFirstByWorkflowCodeOrderByWorkflowVersionDesc(wf.getWorkflowCode());
            long nextVersion = latest.map(w -> w.getWorkflowVersion() + 1).orElse(1L);
            latest.filter(w -> w.getWorkflowStatus() != WorkflowStatus.COMPLETED)
                    .ifPresent(draft -> workFlowService.deleteWorkflow(draft.getWorkflowId()));

            WorkFlowDTO created = workFlowService.createEmptyWorkflow(WorkFlowDTO.builder()
                    .workflowCode(wf.getWorkflowCode())
                    .name(wf.getName())
                    .description(wf.getDescription())
                    .tenantId(wf.getTenantId())
                    .workflowVersion(nextVersion)
                    .createdBy("trellis-seed")
                    .build());

            List<WorkFlowTask> tasks = new ArrayList<>();
            for (JsonNode node : wf.getTasks()) {
                tasks.add(toTask(node));
            }
            workFlowTaskService.saveAll(created.getWorkflowId(), tasks);
            if (wf.isPublish()) {
                workFlowService.publishWorkflow(created.getWorkflowId(), true);
            }
            log.info("[seed:{}] {} v{} created with {} tasks, workflowId={}, published={}", source, wf.getWorkflowCode(),
                    nextVersion, tasks.size(), created.getWorkflowId(), wf.isPublish());
        }
    }
}
