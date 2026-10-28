package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.hooks.HookBean;
import io.trellis.common.model.Hook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Example HookBean for a "beanHook" with config.beanName = "echoHook". Only logs. */
@Slf4j
@Component("echoHook")
public class EchoHookBean implements HookBean {

    @Override
    public void execute(TrellisTaskContext context, Hook hook) {
        log.info("[echoHook] hook={} async={} assetId={} taskId={} status={}", hook.getName(), hook.getAsync(),
                context.getAssetId(), context.getTaskId(), context.getTaskStatus());
    }
}
