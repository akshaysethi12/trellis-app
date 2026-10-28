package io.trellis.app.hooks;

import io.trellis.common.dto.BaseWorkFlowContext;
import io.trellis.common.model.Hook;
import io.trellis.core.dispatcher.AsyncHookDispatcher;
import io.trellis.core.processor.AsyncHookProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Runs hooks marked "async": true on a background thread after the request returns.
 * An in-process pool keeps the service simple. For durability across restarts, replace this bean with one that
 * publishes the hook to a message queue and calls AsyncHookProcessor from the consumer.
 */
@Slf4j
@Component
@SuppressWarnings({"rawtypes", "unchecked"})
public class InProcessAsyncHookDispatcher implements AsyncHookDispatcher {

    private final AsyncHookProcessor asyncHookProcessor;
    private final TaskExecutor executor;

    public InProcessAsyncHookDispatcher(AsyncHookProcessor asyncHookProcessor,
                                        @Qualifier("trellisHookExecutor") TaskExecutor executor) {
        this.asyncHookProcessor = asyncHookProcessor;
        this.executor = executor;
    }

    @Override
    public void dispatch(BaseWorkFlowContext context, Hook hook) {
        log.info("Queued async hook '{}' ({}) for assetId={} taskId={}", hook.getName(), hook.getType(),
                context.getAssetId(), context.getTaskId());
        executor.execute(() -> {
            try {
                asyncHookProcessor.process(context, hook);
            } catch (Exception ex) {
                log.warn("Async hook '{}' failed: {}", hook.getName(), ex.getMessage(), ex);
            }
        });
    }
}
