package io.trellis.app.dependency;

import io.trellis.common.model.NumericTaskActivationActionConfig;
import io.trellis.common.model.TaskDependency;
import io.trellis.core.strategies.TaskDependencyAction;
import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.hooks.HookSupport;
import io.trellis.app.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NUMERIC_TASK_ACTIVATION_ACTION
 * Reads a number from config.path (e.g. "2" or "2 co-applicants") and activates the first N
 * of dependentTaskIds, deactivating the rest. Example: "Number of co-applicants" -> co-applicant 1..N.
 */
@Slf4j
@Component("NUMERIC_TASK_ACTIVATION_ACTION")
@RequiredArgsConstructor
public class NumericTaskActivationAction implements TaskDependencyAction<TrellisTaskContext> {

    private final TemplateEngine templateEngine;
    private final HookSupport hookSupport;
    private final DependentTaskUpdates dependentTaskUpdates;

    @Override
    public void execute(TrellisTaskContext context, TaskDependency dependency) {
        if (!(dependency.getConfig() instanceof NumericTaskActivationActionConfig config)) {
            log.warn("NUMERIC_TASK_ACTIVATION_ACTION without config on taskId={}", context.getTaskId());
            return;
        }
        String selected = templateEngine.resolveAsString(config.getPath(), hookSupport.contextMap(context));
        int count = firstNumber(selected);
        if (count < 0) {
            log.warn("NUMERIC_TASK_ACTIVATION_ACTION: no number in '{}' for taskId={}", selected, context.getTaskId());
            return;
        }
        List<String> ids = dependency.getDependentTaskIds();
        Map<String, Boolean> changes = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            changes.put(ids.get(i), i < count);
        }
        log.info("NUMERIC_TASK_ACTIVATION_ACTION on '{}': value={} -> {}", context.getTaskId(), count, changes);
        dependentTaskUpdates.setActive(context, changes);
    }

    private int firstNumber(String value) {
        if (value == null) {
            return -1;
        }
        String[] parts = value.replaceAll("\\D", " ").trim().split("\\s+");
        try {
            return parts.length == 0 || parts[0].isEmpty() ? -1 : Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
