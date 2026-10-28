package io.trellis.app.context;

import io.trellis.common.dto.BaseWorkFlowContext;
import io.trellis.common.dto.WorkflowInstanceTaskDto;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default context that travels down the Trellis command chain.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class TrellisTaskContext extends BaseWorkFlowContext {

    private String workflowCode;
    private Long workflowVersion;

    /** Tasks whose active flag / status changed because of this answer. Returned to the client. */
    private List<WorkflowInstanceTaskDto> dependentTaskUpdates = new ArrayList<>();

    /** Free-form bag for custom @Task beans, side effects and hooks to pass data to each other. */
    private Map<String, Object> attributes = new HashMap<>();
}
