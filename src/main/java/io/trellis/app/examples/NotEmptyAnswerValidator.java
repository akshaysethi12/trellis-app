package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.exception.TrellisBusinessException;
import io.trellis.validator.validation.AbstractValidator;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Example Java validator in the TaskValidationChain (first step of the command chain).
 * Every answer passes through it. Return true to continue; throw to reject.
 */
@Component
@Order(10)
public class NotEmptyAnswerValidator extends AbstractValidator<TrellisTaskContext> {

    @Override
    protected boolean doValidate(TrellisTaskContext context) {
        if (context.getCapturedValue() == null || context.getCapturedValue().isEmpty()) {
            throw new TrellisBusinessException("capturedValue must not be empty", HttpStatus.BAD_REQUEST, context.getTaskId());
        }
        return true;
    }
}
