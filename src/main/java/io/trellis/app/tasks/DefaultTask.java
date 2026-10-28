package io.trellis.app.tasks;

import io.trellis.common.entity.mongo.CapturedTask;
import io.trellis.common.enums.TaskStatus;
import io.trellis.core.annotations.PostProcess;
import io.trellis.core.annotations.PreProcess;
import io.trellis.core.annotations.Process;
import io.trellis.core.annotations.Task;
import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.util.CapturedValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback handler. TaskRegistry looks up taskId, then taskType, then "default".
 * So every task without its own @Task class lands here.
 */
@Slf4j
@Component
@Task(name = "default")
public class DefaultTask {

    @PreProcess
    public void preProcess(TrellisTaskContext context) {
        log.debug("[DefaultTask] preProcess taskId={} assetId={}", context.getTaskId(), context.getAssetId());
    }

    @Process
    public void process(TrellisTaskContext context) {
        CapturedTask capturedTask = CapturedValues.mergeIncoming(context);
        TaskStatus status = TaskStatusResolver.resolve(context.getWorkFlowTask().getMeta(), capturedTask.getCapturedValue());
        capturedTask.setTaskStatus(status);
        context.setTaskStatus(status);
        log.info("[DefaultTask] taskId={} assetId={} status={}", context.getTaskId(), context.getAssetId(), status);
        // ProcessCommand saves CapturedTaskData right after this method returns.
    }

    @PostProcess
    public void postProcess(TrellisTaskContext context) {
        log.debug("[DefaultTask] postProcess taskId={} status={}", context.getTaskId(), context.getTaskStatus());
    }
}
