package io.trellis.app.hooks;

import io.trellis.common.model.BeanHookConfig;
import io.trellis.common.model.ValidationHook;
import io.trellis.core.strategies.ValidationHookStrategy;
import io.trellis.app.context.TrellisTaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.context.ApplicationContext;

/** "beanValidationHook": runs the {@link ValidationHookBean} named in config.beanName before the save. */
@Slf4j
@Component("beanValidationHook")
@RequiredArgsConstructor
public class BeanValidationHookStrategy implements ValidationHookStrategy<TrellisTaskContext> {

    /** Looked up per call so the runtime starts even when an app defines no ValidationHookBean beans. */
    private final ApplicationContext applicationContext;
    private final HookSupport hookSupport;

    @Override
    public void execute(TrellisTaskContext context, ValidationHook validationHook) {
        if (!(validationHook.getConfig() instanceof BeanHookConfig config) || config.getBeanName() == null) {
            log.warn("beanValidationHook '{}' has no beanName, skipping", validationHook.getName());
            return;
        }
        ValidationHookBean bean = applicationContext.getBeansOfType(ValidationHookBean.class).get(config.getBeanName());
        if (bean == null) {
            log.warn("beanValidationHook '{}': no ValidationHookBean named '{}'", validationHook.getName(), config.getBeanName());
            return;
        }
        hookSupport.enforce(bean.execute(context, validationHook), context.getTaskId());
    }
}
