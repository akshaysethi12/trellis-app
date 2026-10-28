package io.trellis.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "trellis.runtime")
public class TrellisRuntimeProperties {

    /** Tenant used when a request has no "tenantId" header. */
    private String defaultTenant = "DEFAULT";

    /** Value stored in CapturedTaskData.sourceType for executions started through the SDK API. */
    private String sdkSourceType = "SDK";

    private Hooks hooks = new Hooks();
    private Media media = new Media();
    private Seed seed = new Seed();

    @Data
    public static class Hooks {
        private int asyncPoolSize = 4;
    }

    @Data
    public static class Media {
        /** Folder where the local signed-URL emulation stores uploaded files. */
        private String localDir = "./data/media";
        /** Public base URL the phone uses to reach this server. Empty = derive from the request. */
        private String publicBaseUrl = "";
    }

    @Data
    public static class Seed {
        private boolean enabled = true;
        /** Spring resource patterns of seed files. */
        private List<String> locations = new ArrayList<>(List.of("classpath*:trellis-seed/*.json"));
        /** When true, an already published workflowCode gets a new published version on every start. */
        private boolean forceNewVersion = false;
    }
}
