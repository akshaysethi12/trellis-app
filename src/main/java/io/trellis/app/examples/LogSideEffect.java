package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.core.strategies.SideEffectStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Example SideEffectStrategy. Runs for any task whose meta contains "sideEffectBeans": ["logSideEffect"]
 * (usually inside an image/video option). context.getSideEffectData() is the meta node that listed it.
 */
@Slf4j
@Component("logSideEffect")
public class LogSideEffect implements SideEffectStrategy<TrellisTaskContext> {

    @Override
    public void execute(TrellisTaskContext context) {
        log.info("[logSideEffect] assetId={} taskId={} node={} answer={}", context.getAssetId(), context.getTaskId(),
                context.getSideEffectData(), context.getCapturedValue());
        context.getAttributes().put("logSideEffect", "ran");
    }
}
