package io.trellis.app.sdk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Body of PUT /api/v1/sdk/workflow/update, exactly what rn-trellis-core's SyncManager sends. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SdkCaptureRequest {
    private String assetId;
    private String workflowId;
    private String taskId;
    private String capturedBy;
    private Map<String, Object> capturedValue;
}
