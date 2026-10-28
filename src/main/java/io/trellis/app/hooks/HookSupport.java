package io.trellis.app.hooks;

import io.trellis.common.dto.BaseWorkFlowContext;
import io.trellis.common.model.HttpHookConfig;
import io.trellis.common.model.ValidationHookResponse;
import io.trellis.app.exception.TrellisBusinessException;
import io.trellis.app.template.TemplateEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Shared plumbing for the HTTP hook strategies. */
@Slf4j
@Component
public class HookSupport {

    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final Environment environment;
    private final RestClient restClient;

    public HookSupport(ObjectMapper objectMapper, TemplateEngine templateEngine, Environment environment,
                       @Qualifier("trellisRestClient") RestClient restClient) {
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
        this.environment = environment;
        this.restClient = restClient;
    }

    /** The context as a plain map: what ${...} placeholders and SpEL conditions are evaluated against. */
    public Map<String, Object> contextMap(BaseWorkFlowContext context) {
        return objectMapper.convertValue(context, new TypeReference<Map<String, Object>>() {});
    }

    /** Resolves Spring property placeholders such as ${trellis.app.mock-base-url} in hook URLs. */
    public String resolveProperties(String value) {
        return StringUtils.isEmpty(value) ? value : environment.resolvePlaceholders(value);
    }

    public JsonNode buildPayload(Map<String, Object> requestMappings, BaseWorkFlowContext context) {
        Map<String, Object> mappings = requestMappings == null ? new HashMap<>() : new HashMap<>(requestMappings);
        Instant timestamp = context.getRequestTimestamp() != null ? context.getRequestTimestamp() : Instant.now();
        mappings.putIfAbsent("timestamp", timestamp.toEpochMilli());
        return templateEngine.resolve(mappings, contextMap(context));
    }

    public JsonNode call(HttpHookConfig config, JsonNode body) {
        String url = resolveProperties(config.getUrl());
        HttpMethod method = HttpMethod.valueOf(StringUtils.defaultIfBlank(config.getMethod(), "POST").toUpperCase());
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(url);
        if (config.getParams() != null) {
            config.getParams().forEach((k, v) -> uri.queryParam(k, resolveProperties(v)));
        }
        RestClient.RequestBodySpec request = restClient.method(method)
                .uri(uri.build().encode().toUri())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        if (config.getHeaders() != null) {
            config.getHeaders().forEach((k, v) -> request.header(k, resolveProperties(v)));
        }
        if (method != HttpMethod.GET && body != null) {
            request.body(body);
        }
        String raw = request.retrieve().body(String.class);
        try {
            return StringUtils.isBlank(raw) ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("raw", raw);
        }
    }

    /** Turns a failed ValidationHookResponse into a 4xx error for the client. */
    public void enforce(ValidationHookResponse response, String taskId) {
        if (response == null || Boolean.TRUE.equals(response.getSuccess())) {
            return;
        }
        int code = response.getHttpStatusCode() != null ? response.getHttpStatusCode() : 400;
        HttpStatus status = HttpStatus.resolve(code);
        String message = StringUtils.defaultIfBlank(response.getMessage(), "Validation hook failed");
        throw new TrellisBusinessException(message, status != null ? status : HttpStatus.BAD_REQUEST, taskId);
    }
}
