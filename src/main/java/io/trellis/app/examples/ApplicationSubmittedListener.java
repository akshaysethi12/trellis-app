package io.trellis.app.examples;

import io.trellis.app.events.WorkflowSubmittedEvent;
import io.trellis.app.util.CapturedValues;
import io.trellis.common.entity.mongo.CapturedTaskData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow-level webhook: after a loan application is submitted, POST a summary to
 * {trellis.app.mock-base-url}/mock/webhooks/application-submitted (a loan origination system in real life).
 * Task-level webhooks are configured in JSON; workflow-level ones listen to WorkflowSubmittedEvent.
 */
@Slf4j
@Component
public class ApplicationSubmittedListener {

    private final RestClient restClient;
    private final String baseUrl;

    public ApplicationSubmittedListener(@Qualifier("trellisRestClient") RestClient restClient,
                                        @Value("${trellis.app.mock-base-url}") String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @EventListener
    public void onSubmitted(WorkflowSubmittedEvent event) {
        CapturedTaskData data = event.capturedTaskData();
        if (!"LOAN_APPLICATION".equals(data.getWorkflowCode())) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "LOAN_APPLICATION_SUBMITTED");
        body.put("assetId", data.getAssetId());
        body.put("workflowCode", data.getWorkflowCode());
        body.put("version", data.getVersion());
        body.put("submittedBy", event.submittedBy());
        body.put("applicantName", CapturedValues.readText(data, "full_name"));
        body.put("loanAmount", CapturedValues.readText(data, "loan_amount"));
        body.put("tenure", CapturedValues.readOption(data, "tenure_months", "dropdown"));
        body.put("emiSummary", CapturedValues.readText(data, "emi_summary"));
        try {
            restClient.post().uri(baseUrl + "/mock/webhooks/application-submitted")
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            log.info("[applicationSubmitted] assetId={} webhook sent", data.getAssetId());
        } catch (Exception e) {
            // A failed notification must not undo the submit; a real service would retry from an outbox.
            log.warn("[applicationSubmitted] assetId={} webhook failed: {}", data.getAssetId(), e.getMessage());
        }
    }
}
