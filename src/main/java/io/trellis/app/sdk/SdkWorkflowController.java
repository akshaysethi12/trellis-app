package io.trellis.app.sdk;

import io.trellis.app.config.TrellisRuntimeProperties;
import io.trellis.app.media.LocalMediaController;
import io.trellis.app.sdk.dto.SdkCaptureRequest;
import io.trellis.app.sdk.dto.SdkSubmitResponse;
import io.trellis.app.sdk.dto.SdkTaskTreeResponse;
import io.trellis.app.sdk.dto.SdkUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Client-facing execution API (the paths the rn-trellis-core mobile SDK calls).
 * Headers: tenantId (optional, defaults to trellis.runtime.default-tenant), emailId (optional, stored as updatedBy).
 */
@RestController
@RequestMapping("/api/v1/sdk/workflow")
@RequiredArgsConstructor
@Tag(name = "SDK workflow execution", description = "Endpoints called by rn-trellis-core")
public class SdkWorkflowController {

    private final SdkWorkflowService service;
    private final TrellisRuntimeProperties properties;
    private final LocalMediaController media;

    @GetMapping("/full-hierarchy")
    @Operation(summary = "Task tree for an asset, with its saved answers. Creates the answer sheet on first call.")
    public ResponseEntity<SdkTaskTreeResponse> tree(@RequestHeader(value = "tenantId", required = false) String tenantId,
                                                    @RequestParam String assetId, @RequestParam String workflowId) {
        return ResponseEntity.ok(service.getTaskTree(tenant(tenantId), assetId, workflowId, media.mediaBaseUrl()));
    }

    /** rn-trellis-core sends this as POST with optional metadata in the body. */
    @PostMapping("/full-hierarchy")
    public ResponseEntity<SdkTaskTreeResponse> treePost(@RequestHeader(value = "tenantId", required = false) String tenantId,
                                                        @RequestParam String assetId, @RequestParam String workflowId,
                                                        @RequestBody(required = false) Map<String, Object> metadata) {
        return tree(tenantId, assetId, workflowId);
    }

    @GetMapping("/version-snapshot")
    @Operation(summary = "Read-only tree for an older answer-sheet version (see captured_task_data_auditing)")
    public ResponseEntity<SdkTaskTreeResponse> snapshot(@RequestHeader(value = "tenantId", required = false) String tenantId,
                                                        @RequestParam String assetId, @RequestParam String workflowId,
                                                        @RequestParam int version) {
        return ResponseEntity.ok(service.getSnapshot(tenant(tenantId), assetId, workflowId, version, media.mediaBaseUrl()));
    }

    @PutMapping("/update")
    @Operation(summary = "Save one task's answer. Runs the full Trellis command chain.")
    public ResponseEntity<SdkUpdateResponse> update(@RequestHeader(value = "tenantId", required = false) String tenantId,
                                                    @RequestHeader(value = "emailId", required = false) String email,
                                                    @RequestBody SdkCaptureRequest request) {
        if (request.getCapturedBy() == null) {
            request.setCapturedBy(email);
        }
        return ResponseEntity.ok(service.updateTask(tenant(tenantId), request));
    }

    @PutMapping("/submit")
    @Operation(summary = "Checks every active, required task is complete, then marks the execution SUBMITTED")
    public ResponseEntity<SdkSubmitResponse> submit(@RequestHeader(value = "tenantId", required = false) String tenantId,
                                                    @RequestHeader(value = "emailId", required = false) String email,
                                                    @RequestParam String assetId, @RequestParam String workflowId) {
        return ResponseEntity.ok(service.submit(tenant(tenantId), assetId, workflowId, email));
    }

    @PutMapping("/reset")
    @Operation(summary = "Archives the current answers (new version) and starts a blank sheet")
    public ResponseEntity<SdkTaskTreeResponse> reset(@RequestHeader(value = "tenantId", required = false) String tenantId,
                                                     @RequestParam String assetId, @RequestParam String workflowId) {
        return ResponseEntity.ok(service.reset(tenant(tenantId), assetId, workflowId, media.mediaBaseUrl()));
    }

    private String tenant(String header) {
        return StringUtils.defaultIfBlank(header, properties.getDefaultTenant());
    }
}
