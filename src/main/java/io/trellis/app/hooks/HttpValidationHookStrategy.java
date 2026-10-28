package io.trellis.app.hooks;

import io.trellis.common.model.HttpHookConfig;
import io.trellis.common.model.ValidationHook;
import io.trellis.common.model.ValidationHookResponse;
import io.trellis.core.strategies.ValidationHookStrategy;
import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.exception.TrellisBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * "httpValidationHook": asks an external service whether the answer is acceptable, BEFORE it is saved.
 * The service must answer { "success": true|false, "httpStatusCode": 422, "message": "..." }.
 */
@Slf4j
@Component("httpValidationHook")
@RequiredArgsConstructor
public class HttpValidationHookStrategy implements ValidationHookStrategy<TrellisTaskContext> {

    private final HookSupport hookSupport;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(TrellisTaskContext context, ValidationHook validationHook) {
        if (!(validationHook.getConfig() instanceof HttpHookConfig config) || config.getUrl() == null) {
            log.warn("httpValidationHook '{}' has no url, skipping", validationHook.getName());
            return;
        }
        JsonNode response;
        try {
            response = hookSupport.call(config, hookSupport.buildPayload(config.getRequestMappings(), context));
        } catch (Exception ex) {
            throw new TrellisBusinessException("Could not verify this answer right now: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, context.getTaskId());
        }
        hookSupport.enforce(objectMapper.convertValue(response, ValidationHookResponse.class), context.getTaskId());
        log.info("httpValidationHook '{}' passed. taskId={}", validationHook.getName(), context.getTaskId());
    }
}
