package io.trellis.app.hooks;

import io.trellis.common.model.BeanHookConfig;
import io.trellis.common.model.Hook;
import io.trellis.core.strategies.HookStrategy;
import io.trellis.app.context.TrellisTaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.context.ApplicationContext;

/** "beanHook": runs the {@link HookBean} whose Spring bean name is config.beanName. */
@Slf4j
@Component("beanHook")
@RequiredArgsConstructor
public class BeanHookStrategy implements HookStrategy<TrellisTaskContext> {

    /** Looked up per call so the runtime starts even when an app defines no HookBean beans. */
    private final ApplicationContext applicationContext;

    @Override
    public void execute(TrellisTaskContext context, Hook hook) {
        if (!(hook.getConfig() instanceof BeanHookConfig config) || config.getBeanName() == null) {
            log.warn("beanHook '{}' has no beanName, skipping. taskId={}", hook.getName(), context.getTaskId());
            return;
        }
        HookBean bean = applicationContext.getBeansOfType(HookBean.class).get(config.getBeanName());
        if (bean == null) {
            log.warn("beanHook '{}': no HookBean named '{}'", hook.getName(), config.getBeanName());
            return;
        }
        try {
            bean.execute(context, hook);
            log.info("beanHook '{}' ({}) done. assetId={} taskId={}", hook.getName(), config.getBeanName(),
                    context.getAssetId(), context.getTaskId());
        } catch (Exception ex) {
            log.warn("beanHook '{}' ({}) failed: {}", hook.getName(), config.getBeanName(), ex.getMessage(), ex);
        }
    }
}
