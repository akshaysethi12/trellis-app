package io.trellis.app.dependency;

import io.trellis.common.model.TaskDependency;
import io.trellis.common.model.TaskVisibilityActionConfig;
import io.trellis.core.strategies.TaskDependencyAction;
import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.hooks.HookSupport;
import io.trellis.app.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TASK_VISIBILITY_ACTION
 * config.path            ${...} expression read from the context, e.g. ${capturedValue.radioButton.option}
 * config.dependentValue  value to compare with (case-insensitive)
 * config.activate        true  -> dependents are active only when the value matches
 *                        false -> dependents are active only when it does NOT match
 */
@Slf4j
@Component("TASK_VISIBILITY_ACTION")
@RequiredArgsConstructor
public class TaskVisibilityAction implements TaskDependencyAction<TrellisTaskContext> {

    private final TemplateEngine templateEngine;
    private final HookSupport hookSupport;
    private final DependentTaskUpdates dependentTaskUpdates;

    @Override
    public void execute(TrellisTaskContext context, TaskDependency dependency) {
        if (!(dependency.getConfig() instanceof TaskVisibilityActionConfig config)) {
            log.warn("TASK_VISIBILITY_ACTION without TaskVisibilityActionConfig on taskId={}", context.getTaskId());
            return;
        }
        String selected = templateEngine.resolveAsString(config.getPath(), hookSupport.contextMap(context));
        boolean matched = config.getDependentValue() != null && config.getDependentValue().equalsIgnoreCase(selected);
        boolean activate = Boolean.TRUE.equals(config.getActivate()) ? matched : !matched;
        Map<String, Boolean> changes = new LinkedHashMap<>();
        dependency.getDependentTaskIds().forEach(id -> changes.put(id, activate));
        log.info("TASK_VISIBILITY_ACTION on '{}': selected='{}' expected='{}' -> active={} for {}", context.getTaskId(),
                selected, config.getDependentValue(), activate, dependency.getDependentTaskIds());
        dependentTaskUpdates.setActive(context, changes);
    }
}
