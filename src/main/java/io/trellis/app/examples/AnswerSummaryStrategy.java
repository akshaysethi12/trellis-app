package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.client.clients.WorkFlowClientService;
import io.trellis.core.strategies.DependentTaskStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Example DependentTaskStrategy, wired with "dependencyBean": "answerSummary" on a task.
 * Runs after the answer is saved and records it under captured_task_data.additionalData.<taskId>.
 */
@Slf4j
@Component("answerSummary")
@RequiredArgsConstructor
public class AnswerSummaryStrategy implements DependentTaskStrategy<TrellisTaskContext> {

    private final WorkFlowClientService workFlowClientService;

    @Override
    public void execute(TrellisTaskContext context) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("answer", context.getCapturedValue());
        summary.put("status", context.getTaskStatus());
        summary.put("recordedAt", Instant.now().toString());
        context.getCapturedTaskData().getAdditionalData().put(context.getTaskId(), summary);
        context.setCapturedTaskData(workFlowClientService.saveCapturedData(context.getCapturedTaskData()));
        log.info("[answerSummary] assetId={} taskId={} summary={}", context.getAssetId(), context.getTaskId(), summary);
    }
}
