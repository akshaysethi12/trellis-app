package io.trellis.app.dependency;

import io.trellis.common.dto.WorkflowInstanceTaskDto;
import io.trellis.common.entity.mongo.CapturedTask;
import io.trellis.common.entity.mongo.CapturedTaskData;
import io.trellis.common.entity.mongo.WorkFlowTask;
import io.trellis.core.mapper.WorkFlowInstanceTaskMapper;
import io.trellis.client.clients.WorkFlowClientService;
import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.util.CapturedValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns other tasks on or off, saves the change, and records it in context.dependentTaskUpdates
 * so the phone can refresh those tasks without re-fetching the whole tree.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DependentTaskUpdates {

    private final WorkFlowClientService workFlowClientService;
    private final WorkFlowInstanceTaskMapper mapper;

    public void setActive(TrellisTaskContext context, Map<String, Boolean> activeByTaskId) {
        if (activeByTaskId.isEmpty()) {
            return;
        }
        CapturedTaskData data = context.getCapturedTaskData();
        activeByTaskId.forEach((taskId, active) -> {
            CapturedTask task = CapturedValues.getOrCreate(data, taskId);
            task.setActive(active);
            log.info("Dependency: task '{}' active={} (triggered by '{}', assetId={})", taskId, active,
                    context.getTaskId(), context.getAssetId());
        });
        context.setCapturedTaskData(workFlowClientService.saveCapturedData(data));

        report(context, List.copyOf(activeByTaskId.keySet()));
    }

    /**
     * Adds the current state of these tasks to the response's dependentTaskUpdates, so the client can re-render them.
     * Call after saving captured data.
     */
    public void report(TrellisTaskContext context, List<String> ids) {
        CapturedTaskData data = context.getCapturedTaskData();
        Map<String, WorkFlowTask> blueprint = workFlowClientService.fetchWorkflowTasks(context.getWorkflowId(), ids)
                .stream().collect(Collectors.toMap(WorkFlowTask::getTaskId, Function.identity()));
        for (String taskId : ids) {
            WorkFlowTask taskEntity = blueprint.get(taskId);
            if (taskEntity == null) {
                continue;
            }
            WorkflowInstanceTaskDto dto = mapper.mapToWorkflowTaskDto(taskEntity, data.getCapturedTasks().get(taskId));
            context.getDependentTaskUpdates().removeIf(existing -> taskId.equals(existing.getTaskId()));
            context.getDependentTaskUpdates().add(dto);
        }
    }
}
