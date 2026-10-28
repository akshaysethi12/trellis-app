package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.hooks.ValidationHookBean;
import io.trellis.app.util.CapturedValues;
import io.trellis.common.model.BeanHookConfig;
import io.trellis.common.model.ValidationHook;
import io.trellis.common.model.ValidationHookResponse;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * "beanValidationHook" with config.beanName = "numberRangeValidator".
 * Reusable: the limits come from the task's JSON, config.metadata = {"min": .., "max": .., "label": ".."}.
 * Used for monthly income and loan amount with different limits and no extra Java.
 */
@Component("numberRangeValidator")
public class NumberRangeValidationHook implements ValidationHookBean {

    @Override
    public ValidationHookResponse execute(TrellisTaskContext context, ValidationHook validationHook) {
        Map<String, Object> metadata = validationHook.getConfig() instanceof BeanHookConfig cfg && cfg.getMetadata() != null
                ? cfg.getMetadata() : Map.of();
        double min = metadata.get("min") instanceof Number n ? n.doubleValue() : 0;
        double max = metadata.get("max") instanceof Number n ? n.doubleValue() : Double.MAX_VALUE;
        String label = metadata.get("label") instanceof String s ? s : "Amount";

        Object textBox = context.getCapturedValue().get("textBox");
        String raw = textBox instanceof Map<?, ?> m && m.get("value") != null ? m.get("value").toString() : null;
        Double amount = CapturedValues.parseAmount(raw);

        ValidationHookResponse response = new ValidationHookResponse();
        response.setSuccess(amount != null && amount >= min && amount <= max);
        if (!response.getSuccess()) {
            NumberFormat format = NumberFormat.getIntegerInstance(Locale.of("en", "IN"));
            response.setHttpStatusCode(422);
            response.setMessage(amount == null
                    ? label + " must be a number"
                    : label + " must be between " + format.format(min) + " and " + format.format(max));
        }
        return response;
    }
}
