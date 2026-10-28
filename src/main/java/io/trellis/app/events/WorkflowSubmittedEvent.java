package io.trellis.app.events;

import io.trellis.common.entity.mongo.CapturedTaskData;

/** Published after a workflow execution passes submit validation. Listen with @EventListener. */
public record WorkflowSubmittedEvent(CapturedTaskData capturedTaskData, String submittedBy) {
}
