package io.trellis.app.validation;

import io.trellis.validator.validation.AbstractValidator;
import io.trellis.validator.validation.TaskValidationChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Adds every AbstractValidator bean to Trellis's TaskValidationChain, ordered by @Order.
 *
 * Contract of AbstractValidator.doValidate: return true to continue the chain, throw to reject.
 * Returning false silently stops the remaining validators.
 */
@Slf4j
@Component
@SuppressWarnings({"rawtypes", "unchecked"})
public class TrellisValidatorRegistrar implements SmartInitializingSingleton {

    private final TaskValidationChain chain;
    private final ObjectProvider<AbstractValidator> validators;

    public TrellisValidatorRegistrar(TaskValidationChain chain, ObjectProvider<AbstractValidator> validators) {
        this.chain = chain;
        this.validators = validators;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validators.orderedStream().forEach(validator -> {
            chain.addValidator(validator);
            log.info("Registered Trellis validator {}", validator.getClass().getSimpleName());
        });
    }
}
