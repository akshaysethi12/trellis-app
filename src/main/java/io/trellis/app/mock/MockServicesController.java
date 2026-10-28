package io.trellis.app.mock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local stand-ins for external systems, so hooks can be tested without anything else running:
 *   POST /mock/webhooks/{channel}  receives httpHook calls; stored in the mock_webhooks collection
 *   GET  /mock/webhooks            lists what arrived
 *   POST /mock/validate            target for an httpValidationHook: rejects values containing "invalid"
 */
@Slf4j
@RestController
@RequestMapping("/mock")
@RequiredArgsConstructor
@Tag(name = "Mock external services", description = "Webhook receiver and fake validation API for local testing")
public class MockServicesController {

    private static final String COLLECTION = "mock_webhooks";

    private final MongoTemplate mongoTemplate;

    @PostMapping("/webhooks/{channel}")
    @Operation(summary = "Receives a webhook and stores it")
    public Map<String, Object> receive(@PathVariable String channel, @RequestBody(required = false) Map<String, Object> body) {
        mongoTemplate.insert(new Document("channel", channel).append("body", body).append("receivedAt", new Date()), COLLECTION);
        log.info("[mock] webhook '{}' received: {}", channel, body);
        return Map.of("received", true, "channel", channel);
    }

    @GetMapping("/webhooks")
    @Operation(summary = "Lists received webhooks, newest first")
    public List<Document> list(@RequestParam(required = false) String channel) {
        Query query = channel == null ? new Query() : Query.query(Criteria.where("channel").is(channel));
        List<Document> docs = mongoTemplate.find(query.with(Sort.by(Sort.Direction.DESC, "receivedAt")).limit(50), Document.class, COLLECTION);
        docs.forEach(d -> d.put("_id", String.valueOf(d.get("_id"))));
        return docs;
    }

    @DeleteMapping("/webhooks")
    @Operation(summary = "Clears received webhooks")
    public Map<String, Object> clear() {
        long removed = mongoTemplate.remove(new Query(), COLLECTION).getDeletedCount();
        return Map.of("removed", removed);
    }

    @PostMapping("/validate")
    @Operation(summary = "Fake validation service for httpValidationHook. Fails when value contains 'invalid'.")
    public Map<String, Object> validate(@RequestBody Map<String, Object> body) {
        String value = String.valueOf(body.getOrDefault("value", ""));
        Map<String, Object> response = new LinkedHashMap<>();
        boolean ok = !value.isBlank() && !value.toLowerCase().contains("invalid");
        response.put("success", ok);
        if (!ok) {
            response.put("httpStatusCode", 422);
            response.put("message", "External validation rejected '" + value + "'");
        }
        return response;
    }
}
