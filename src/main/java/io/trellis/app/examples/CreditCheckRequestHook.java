package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.hooks.HookBean;
import io.trellis.app.util.CapturedValues;
import io.trellis.common.model.Hook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HookBean for a "beanHook" with config.beanName = "creditCheckRequest", marked "async": true on the consent task.
 * Runs on a background thread after the response is sent. A real service would call a credit bureau here.
 */
@Slf4j
@Component("creditCheckRequest")
public class CreditCheckRequestHook implements HookBean {

    @Override
    public void execute(TrellisTaskContext context, Hook hook) {
        String pan = CapturedValues.readText(context.getCapturedTaskData(), "pan_number");
        String masked = pan == null || pan.length() < 4 ? "unknown" : "******" + pan.substring(pan.length() - 4);
        log.info("[creditCheckRequest] hook={} async={} assetId={} pan={} -> credit check requested",
                hook.getName(), hook.getAsync(), context.getAssetId(), masked);
    }
}
