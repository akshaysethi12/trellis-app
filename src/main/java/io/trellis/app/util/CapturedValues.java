package io.trellis.app.util;

import io.trellis.common.dto.BaseWorkFlowContext;
import io.trellis.common.entity.mongo.CapturedTask;
import io.trellis.common.entity.mongo.CapturedTaskData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helpers for reading and merging answers stored in CapturedTaskData.capturedTasks.
 */
public final class CapturedValues {

    /** Components stored as lists of media items keyed by hashKey. */
    public static final Set<String> MEDIA_COMPONENTS = Set.of("image", "video");

    private CapturedValues() {
    }

    public static CapturedTask getOrCreate(CapturedTaskData data, String taskId) {
        if (data.getCapturedTasks() == null) {
            data.setCapturedTasks(new HashMap<>());
        }
        return data.getCapturedTasks().computeIfAbsent(taskId, id -> new CapturedTask());
    }

    /**
     * Merges context.capturedValue (the new answer) into the stored answer for context.taskId.
     * Plain components are replaced; image/video lists are merged by hashKey so a second photo
     * does not wipe the first one.
     */
    public static CapturedTask mergeIncoming(BaseWorkFlowContext context) {
        CapturedTaskData data = context.getCapturedTaskData();
        CapturedTask task = getOrCreate(data, context.getTaskId());
        Map<String, Object> merged = task.getCapturedValue() == null
                ? new HashMap<>() : new HashMap<>(task.getCapturedValue());
        Map<String, Object> incoming = context.getCapturedValue() == null ? Map.of() : context.getCapturedValue();

        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (MEDIA_COMPONENTS.contains(key) && value instanceof List<?> incomingList) {
                merged.put(key, mergeMedia(merged.get(key), incomingList));
            } else {
                merged.put(key, value);
            }
        }
        task.setCapturedValue(merged);
        task.setActive(true);
        task.setLastUpdated(LocalDateTime.now());
        if (context.getUpdatedBy() != null) {
            data.setUpdatedBy(context.getUpdatedBy());
        }
        return task;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> mergeMedia(Object existing, List<?> incoming) {
        Map<String, Object> byKey = new LinkedHashMap<>();
        List<Object> withoutKey = new ArrayList<>();
        if (existing instanceof List<?> existingList) {
            for (Object item : existingList) {
                addMedia(byKey, withoutKey, item);
            }
        }
        for (Object item : incoming) {
            addMedia(byKey, withoutKey, item);
        }
        List<Object> result = new ArrayList<>(byKey.values());
        result.addAll(withoutKey);
        return result;
    }

    private static void addMedia(Map<String, Object> byKey, List<Object> withoutKey, Object item) {
        if (item instanceof Map<?, ?> map && map.get("hashKey") instanceof String hashKey && !hashKey.isBlank()) {
            byKey.put(hashKey, item);
        } else {
            withoutKey.add(item);
        }
    }

    /** Reads a nested value such as ("full_name", "textBox", "value"). Returns null when any part is missing. */
    public static Object read(CapturedTaskData data, String taskId, String... path) {
        if (data == null || data.getCapturedTasks() == null) {
            return null;
        }
        CapturedTask task = data.getCapturedTasks().get(taskId);
        if (task == null || task.getCapturedValue() == null) {
            return null;
        }
        Object current = task.getCapturedValue();
        for (String part : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    public static String readText(CapturedTaskData data, String taskId) {
        Object value = read(data, taskId, "textBox", "value");
        return value == null ? null : value.toString().trim();
    }

    public static String readOption(CapturedTaskData data, String taskId, String component) {
        Object value = read(data, taskId, component, "option");
        return value == null ? null : value.toString();
    }

    /** Parses "5,00,000", "500000", "₹ 5 lakh" style input into a number; returns null if there are no digits. */
    public static Double parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9.]", "");
        if (digits.isEmpty() || digits.equals(".")) {
            return null;
        }
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
