package io.trellis.app.sdk.dto;

import io.trellis.common.dto.WorkflowInstanceTaskDto;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Response of PUT /api/v1/sdk/workflow/submit (rn-trellis-core SubmitResponse). */
@Data
@Builder
public class SdkSubmitResponse {
    private boolean success;
    private String message;
    /** Present when success=false: a synthetic parent whose childTasks are the tasks still missing. */
    private IncompleteTasks incompleteTasks;
    private Integer version;

    @Data
    @Builder
    public static class IncompleteTasks {
        private List<WorkflowInstanceTaskDto> childTasks;
    }
}
