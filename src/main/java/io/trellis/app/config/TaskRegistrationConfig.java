package io.trellis.app.config;

import io.trellis.core.registry.TaskRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Configuration;

/**
 * Fills Trellis's TaskRegistry with every @Task bean (including DefaultTask, registered as "default").
 * <p>
 * Trellis does not register tasks by itself: the host application must call registerAllTasks() once.
 * Without it, every answer fails with "No task found for taskId: ..., taskType: ...".
 * Runs after all singletons are created, so every @Task bean exists by then.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TaskRegistrationConfig implements SmartInitializingSingleton {

    private final TaskRegistry taskRegistry;

    @Override
    public void afterSingletonsInstantiated() {
        log.info("Registering @Task beans with the Trellis task registry");
        taskRegistry.registerAllTasks();
    }
}
