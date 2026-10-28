package io.trellis.app.hooks;

import io.trellis.common.model.ValidationHook;
import io.trellis.common.model.ValidationHookResponse;
import io.trellis.app.context.TrellisTaskContext;

/**
 * Custom Java check run by a "beanValidationHook" before the answer is saved.
 * Return success=false (with httpStatusCode and message) to reject the answer.
 */
public interface ValidationHookBean {
    ValidationHookResponse execute(TrellisTaskContext context, ValidationHook validationHook);
}
