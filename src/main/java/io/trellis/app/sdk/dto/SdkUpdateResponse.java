package io.trellis.app.sdk.dto;

import io.trellis.common.dto.WorkflowInstanceTaskDto;
import io.trellis.common.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Response of PUT /api/v1/sdk/workflow/update (rn-trellis-core TaskUpdateResponse). */
@Data
@Builder
public class SdkUpdateResponse {
    private String taskId;
    private TaskStatus taskStatus;
    private Map<String, Object> capturedValue;
    private List<WorkflowInstanceTaskDto> dependentTaskUpdates;
    private Integer version;
}
