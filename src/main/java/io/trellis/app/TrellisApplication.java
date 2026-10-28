package io.trellis.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable Trellis service. Scans io.trellis so it picks up every module:
 * trellis-api controllers, the trellis-core engine and the host beans in this module.
 */
@SpringBootApplication(scanBasePackages = "io.trellis")
public class TrellisApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrellisApplication.class, args);
    }
}
