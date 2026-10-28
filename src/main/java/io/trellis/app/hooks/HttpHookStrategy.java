package io.trellis.app.hooks;

import io.trellis.common.model.Hook;
import io.trellis.common.model.HttpHookConfig;
import io.trellis.core.strategies.HookStrategy;
import io.trellis.app.context.TrellisTaskContext;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * "httpHook": calls an external URL (a webhook) after the answer is saved.
 * config.requestMappings is a JSON template; ${...} values are filled from the task context.
 * Failures are logged, never thrown: a broken webhook must not block the inspector.
 */
@Slf4j
@Component("httpHook")
@RequiredArgsConstructor
public class HttpHookStrategy implements HookStrategy<TrellisTaskContext> {

    private final HookSupport hookSupport;

    @Override
    public void execute(TrellisTaskContext context, Hook hook) {
        if (!(hook.getConfig() instanceof HttpHookConfig config) || config.getUrl() == null) {
            log.warn("httpHook '{}' has no HttpHookConfig/url, skipping. taskId={}", hook.getName(), context.getTaskId());
            return;
        }
        try {
            JsonNode body = hookSupport.buildPayload(config.getRequestMappings(), context);
            JsonNode response = hookSupport.call(config, body);
            log.info("httpHook '{}' -> {} {} done. assetId={} taskId={} response={}", hook.getName(),
                    config.getMethod(), hookSupport.resolveProperties(config.getUrl()), context.getAssetId(),
                    context.getTaskId(), response);
        } catch (Exception ex) {
            log.warn("httpHook '{}' failed. assetId={} taskId={} error={}", hook.getName(), context.getAssetId(),
                    context.getTaskId(), ex.getMessage());
        }
    }
}
