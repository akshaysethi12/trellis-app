package io.trellis.app.exception;

import io.trellis.common.exceptions.CapturedTaskFetchException;
import io.trellis.common.exceptions.TaskFetchException;
import io.trellis.common.exceptions.WorkflowNotFoundException;
import io.trellis.common.exceptions.WorkflowNotPublishedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs before trellis-api's GlobalExceptionHandler (which maps everything else to 500).
 * The JSON shape {success, message, status} is what rn-trellis-core reads as the error message.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrellisRuntimeExceptionHandler {

    @ExceptionHandler(TrellisBusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(TrellisBusinessException ex) {
        return body(ex.getStatus(), ex.getMessage(), ex.getTaskId());
    }

    @ExceptionHandler({WorkflowNotFoundException.class, CapturedTaskFetchException.class, TaskFetchException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(WorkflowNotPublishedException.class)
    public ResponseEntity<Map<String, Object>> handleNotPublished(WorkflowNotPublishedException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(OptimisticLockingFailureException ex) {
        log.warn("Concurrent update detected: {}", ex.getMessage());
        return body(HttpStatus.CONFLICT, "The record was changed by another update. Retry the request.", null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, String taskId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status.value());
        body.put("message", message);
        body.put("error", message);
        if (taskId != null) {
            body.put("taskId", taskId);
        }
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
