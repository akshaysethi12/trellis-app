package io.trellis.app.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves ${path.to.value} placeholders in hook request mappings against the task context (as a JSON tree).
 * A value that is exactly one placeholder keeps its JSON type; mixed strings are interpolated.
 */
@Component
@RequiredArgsConstructor
public class TemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
    private static final String PLACEHOLDER_START = "${";
    private static final String PLACEHOLDER_END = "}";

    private final ObjectMapper objectMapper;

    public JsonNode resolve(Object node, Object data){
        return resolve(objectMapper.valueToTree(node), objectMapper.valueToTree(data));
    }

    public String resolveAsString(String value, Object data) {
        return stringify(resolve(value, data));
    }

    public JsonNode resolve(JsonNode node, JsonNode data){
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();

            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                result.set(entry.getKey(), resolve(entry.getValue(),data));
            }
            return result;
        }

        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();

            for (JsonNode child : node) {
                result.add(resolve(child,data));
            }

            return result;
        }

        if (node.isTextual()) {
            return resolve(node.asText(),data);
        }

        return node.deepCopy();
    }

    public JsonNode resolve(String value, JsonNode data) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (!value.contains("${")) {
            return TextNode.valueOf(value);
        }

        if (isCompletePlaceholder(value)) {
            String expression = value.substring(PLACEHOLDER_START.length(), value.length() - PLACEHOLDER_END.length());
            JsonNode resolved = resolvePath(resolveAsString(expression, data).trim(), data);
            return resolved != null ? resolved : TextNode.valueOf(value);
        }

        return TextNode.valueOf(resolvePlaceholdersAsString(value, data));
    }

    private String resolvePlaceholdersAsString(String value, JsonNode data) {
        String resolved = resolvePlaceholdersOnce(value, data);
        if (resolved.equals(value) || !resolved.contains(PLACEHOLDER_START)) {
            return resolved;
        }
        return resolvePlaceholdersAsString(resolved, data);
    }

    private String resolvePlaceholdersOnce(String value, JsonNode data) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder resolvedValue = new StringBuilder();
        while (matcher.find()) {
            String placeholder = matcher.group(0);
            JsonNode resolvedNode = resolvePath(resolveAsString(matcher.group(1), data).trim(), data);
            matcher.appendReplacement(
                    resolvedValue,
                    Matcher.quoteReplacement(resolvedNode != null ? stringify(resolvedNode) : placeholder)
            );
        }
        matcher.appendTail(resolvedValue);

        return resolvedValue.toString();
    }

    private JsonNode resolvePath(String path, JsonNode data) {
        JsonNode current = data;
        for (String part : path.split("\\.")) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return null;
            }
            if (current.isArray() && part.matches("\\d+")) {
                current = current.path(Integer.parseInt(part));
            } else {
                current = current.path(part);
            }
        }
        return current == null || current.isMissingNode() ? null : current;
    }

    private boolean isCompletePlaceholder(String value) {
        if (!value.startsWith(PLACEHOLDER_START) || !value.endsWith(PLACEHOLDER_END)) {
            return false;
        }

        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.startsWith(PLACEHOLDER_START, i)) {
                depth++;
                i++;
            } else if (value.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    return i == value.length() - 1;
                }
            }
        }
        return false;
    }

    private String stringify(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }
}
