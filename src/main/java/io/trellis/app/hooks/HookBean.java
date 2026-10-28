package io.trellis.app.hooks;

import io.trellis.common.model.Hook;
import io.trellis.app.context.TrellisTaskContext;

/**
 * Custom Java code run by a "beanHook". The Spring bean name is what the task's hook config refers to:
 * { "type": "beanHook", "config": { "beanName": "creditBureauPull" } }
 */
public interface HookBean {
    void execute(TrellisTaskContext context, Hook hook);
}
