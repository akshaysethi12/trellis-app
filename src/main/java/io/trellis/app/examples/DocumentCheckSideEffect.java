package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.core.strategies.SideEffectStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SideEffectStrategy listed on image options as "sideEffectBeans": ["documentCheck"].
 * Runs after a document photo is saved. A real service would queue OCR, face match or a fraud check here;
 * this one logs which document arrived. context.getSideEffectData() is the image option that listed it.
 */
@Slf4j
@Component("documentCheck")
public class DocumentCheckSideEffect implements SideEffectStrategy<TrellisTaskContext> {

    @Override
    public void execute(TrellisTaskContext context) {
        log.info("[documentCheck] assetId={} taskId={} option={} -> document queued for verification",
                context.getAssetId(), context.getTaskId(), context.getSideEffectData());
        context.getAttributes().put("documentCheck", "queued");
    }
}
