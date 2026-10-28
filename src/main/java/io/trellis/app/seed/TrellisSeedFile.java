package io.trellis.app.seed;

import io.trellis.common.dto.TaskTypeDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape of a seed file under src/main/resources/trellis-seed/*.json:
 * { "taskTypes": [ ...TaskTypeDTO ], "workflows": [ { workflowCode, name, description, tenantId, publish, tasks: [ ...WorkFlowTask ] } ] }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrellisSeedFile {

    private List<TaskTypeDTO> taskTypes = new ArrayList<>();
    private List<SeedWorkflow> workflows = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeedWorkflow {
        private String workflowCode;
        private String name;
        private String description;
        private String tenantId;
        private boolean publish = true;
        /** Kept as raw JSON and converted per load, so hook configs are parsed straight from the file. */
        private List<JsonNode> tasks = new ArrayList<>();
    }
}
