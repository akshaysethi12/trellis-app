package io.trellis.app.tasks;

import io.trellis.common.enums.TaskStatus;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides COMPLETED / IN_PROGRESS / PENDING for a leaf task by comparing the task's meta
 * (which components are "required") with the stored answer.
 *
 * meta.textBox.required = true      -> capturedValue.textBox.value must be non-blank
 * meta.radioButton.required = true  -> capturedValue.radioButton.option must be set
 * meta.image.options[i].required    -> capturedValue.image must contain that option's hashKey
 */
public final class TaskStatusResolver {

    private TaskStatusResolver() {
    }

    public static TaskStatus resolve(Map<String, Object> meta, Map<String, Object> captured) {
        boolean hasAnswer = captured != null && captured.values().stream().anyMatch(TaskStatusResolver::hasValue);
        if (meta == null) {
            return hasAnswer ? TaskStatus.COMPLETED : TaskStatus.PENDING;
        }
        boolean anyRequired = false;
        boolean allSatisfied = true;
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> component)) {
                continue;
            }
            Object answer = captured == null ? null : captured.get(entry.getKey());
            if (Boolean.TRUE.equals(component.get("required"))) {
                anyRequired = true;
                if (!hasValue(answer)) {
                    allSatisfied = false;
                }
            }
            if (component.get("options") instanceof List<?> options) {
                Set<String> requiredKeys = new HashSet<>();
                for (Object option : options) {
                    if (option instanceof Map<?, ?> opt && Boolean.TRUE.equals(opt.get("required"))
                            && opt.get("hashKey") instanceof String key) {
                        requiredKeys.add(key);
                    }
                }
                if (!requiredKeys.isEmpty()) {
                    anyRequired = true;
                    if (!capturedHashKeys(answer).containsAll(requiredKeys)) {
                        allSatisfied = false;
                    }
                }
            }
        }
        if (!anyRequired) {
            return hasAnswer ? TaskStatus.COMPLETED : TaskStatus.PENDING;
        }
        if (allSatisfied) {
            return TaskStatus.COMPLETED;
        }
        return hasAnswer ? TaskStatus.IN_PROGRESS : TaskStatus.PENDING;
    }

    /** True when the task's meta marks at least one component (or media option) as required. */
    public static boolean isMandatory(Map<String, Object> meta) {
        if (meta == null) {
            return false;
        }
        for (Object value : meta.values()) {
            if (value instanceof Map<?, ?> component) {
                if (Boolean.TRUE.equals(component.get("required"))) {
                    return true;
                }
                if (component.get("options") instanceof List<?> options) {
                    for (Object option : options) {
                        if (option instanceof Map<?, ?> opt && Boolean.TRUE.equals(opt.get("required"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static Set<String> capturedHashKeys(Object answer) {
        Set<String> keys = new HashSet<>();
        if (answer instanceof Collection<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map && map.get("hashKey") instanceof String key) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        if (value instanceof Collection<?> c) {
            return c.stream().anyMatch(TaskStatusResolver::hasValue);
        }
        if (value instanceof Map<?, ?> m) {
            return m.values().stream().anyMatch(TaskStatusResolver::hasValue);
        }
        return true;
    }
}
