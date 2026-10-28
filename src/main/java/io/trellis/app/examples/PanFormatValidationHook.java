package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.hooks.ValidationHookBean;
import io.trellis.common.model.ValidationHook;
import io.trellis.common.model.ValidationHookResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * "beanValidationHook" with config.beanName = "panFormatValidator".
 * A PAN is 5 letters, 4 digits, 1 letter (e.g. ABCDE1234F). Lower-case input is accepted.
 */
@Component("panFormatValidator")
public class PanFormatValidationHook implements ValidationHookBean {

    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    @Override
    public ValidationHookResponse execute(TrellisTaskContext context, ValidationHook validationHook) {
        Object textBox = context.getCapturedValue().get("textBox");
        String value = textBox instanceof Map<?, ?> m && m.get("value") != null
                ? m.get("value").toString().trim().toUpperCase() : "";
        ValidationHookResponse response = new ValidationHookResponse();
        response.setSuccess(PAN.matcher(value).matches());
        if (!response.getSuccess()) {
            response.setHttpStatusCode(422);
            response.setMessage("Enter a valid PAN, for example ABCDE1234F");
        }
        return response;
    }
}
