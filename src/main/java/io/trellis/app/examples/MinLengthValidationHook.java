package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.hooks.ValidationHookBean;
import io.trellis.common.model.ValidationHook;
import io.trellis.common.model.ValidationHookResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Example ValidationHookBean for a "beanValidationHook" with config.beanName = "minLengthValidator".
 * Rejects a text answer shorter than config.metadata.minLength (default 3).
 */
@Component("minLengthValidator")
public class MinLengthValidationHook implements ValidationHookBean {

    @Override
    public ValidationHookResponse execute(TrellisTaskContext context, ValidationHook validationHook) {
        int min = 3;
        if (validationHook.getConfig() instanceof io.trellis.common.model.BeanHookConfig cfg
                && cfg.getMetadata() != null && cfg.getMetadata().get("minLength") instanceof Number n) {
            min = n.intValue();
        }
        Object textBox = context.getCapturedValue().get("textBox");
        String value = textBox instanceof Map<?, ?> m && m.get("value") != null ? m.get("value").toString().trim() : "";
        ValidationHookResponse response = new ValidationHookResponse();
        response.setSuccess(value.length() >= min);
        if (!response.getSuccess()) {
            response.setHttpStatusCode(422);
            response.setMessage("Enter at least " + min + " characters");
        }
        return response;
    }
}
