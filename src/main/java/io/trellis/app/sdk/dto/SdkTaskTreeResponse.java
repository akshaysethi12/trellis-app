package io.trellis.app.sdk.dto;

import io.trellis.common.dto.WorkflowInstanceTaskDto;
import io.trellis.common.enums.WorkflowCaptureTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Response of GET/POST /api/v1/sdk/workflow/full-hierarchy (shape expected by rn-trellis-core). */
@Data
@Builder
public class SdkTaskTreeResponse {
    private String assetId;
    private String workflowId;
    private String workflowCode;
    private Long workflowVersion;
    private String workflowExecutionId;
    private Integer version;
    private WorkflowCaptureTaskStatus status;
    private Boolean isEditable;
    private String mediaUrl;
    private List<WorkflowInstanceTaskDto> tasksTree;
}
