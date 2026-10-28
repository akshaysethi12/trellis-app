package io.trellis.app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Thrown by validators, validation hooks and custom tasks to reject an answer with a readable message. */
@Getter
public class TrellisBusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String taskId;

    public TrellisBusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST, null);
    }

    public TrellisBusinessException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public TrellisBusinessException(String message, HttpStatus status, String taskId) {
        super(message);
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
        this.taskId = taskId;
    }

    /** @Task methods are called through reflection, so their exceptions arrive wrapped. Find ours. */
    public static TrellisBusinessException findIn(Throwable throwable) {
        Throwable current = throwable;
        int guard = 0;
        while (current != null && guard++ < 10) {
            if (current instanceof TrellisBusinessException tbe) {
                return tbe;
            }
            current = current.getCause();
        }
        return null;
    }
}
