package io.trellis.app.examples;

import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.dependency.DependentTaskUpdates;
import io.trellis.app.util.CapturedValues;
import io.trellis.client.clients.WorkFlowClientService;
import io.trellis.common.entity.mongo.CapturedTask;
import io.trellis.common.entity.mongo.CapturedTaskData;
import io.trellis.common.enums.TaskStatus;
import io.trellis.core.strategies.DependentTaskStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DependentTaskStrategy wired with "dependencyBean": "emiCalculator" on loan_amount and tenure_months.
 * Runs after either answer is saved. When both are known it computes the monthly EMI and writes it into
 * the system task "emi_summary", which the client receives in dependentTaskUpdates.
 */
@Slf4j
@Component("emiCalculator")
@RequiredArgsConstructor
public class EmiCalculatorStrategy implements DependentTaskStrategy<TrellisTaskContext> {

    static final String EMI_TASK_ID = "emi_summary";
    static final double ANNUAL_RATE_PERCENT = 14.0;

    private final WorkFlowClientService workFlowClientService;
    private final DependentTaskUpdates dependentTaskUpdates;

    @Override
    public void execute(TrellisTaskContext context) {
        CapturedTaskData data = context.getCapturedTaskData();
        Double principal = CapturedValues.parseAmount(CapturedValues.readText(data, "loan_amount"));
        Double months = CapturedValues.parseAmount(CapturedValues.readOption(data, "tenure_months", "dropdown"));
        if (principal == null || months == null || months <= 0) {
            log.info("[emiCalculator] assetId={} waiting for loan_amount and tenure_months", context.getAssetId());
            return;
        }

        long emi = Math.round(emi(principal, months.intValue(), ANNUAL_RATE_PERCENT));
        long totalPayable = emi * months.longValue();
        NumberFormat inr = NumberFormat.getIntegerInstance(Locale.of("en", "IN"));
        String summary = "EMI Rs " + inr.format(emi) + " per month for " + months.intValue() + " months at "
                + ANNUAL_RATE_PERCENT + "% p.a. (total Rs " + inr.format(totalPayable) + ")";

        CapturedTask task = CapturedValues.getOrCreate(data, EMI_TASK_ID);
        task.setCapturedValue(Map.of(
                "textBox", Map.of("value", summary),
                "emi", emi,
                "totalPayable", totalPayable,
                "annualRatePercent", ANNUAL_RATE_PERCENT));
        task.setTaskStatus(TaskStatus.COMPLETED);
        task.setActive(true);
        task.setLastUpdated(LocalDateTime.now());
        context.setCapturedTaskData(workFlowClientService.saveCapturedData(data));
        dependentTaskUpdates.report(context, List.of(EMI_TASK_ID));
        log.info("[emiCalculator] assetId={} principal={} months={} -> {}", context.getAssetId(), principal, months, summary);
    }

    /** Standard reducing-balance EMI: P * r * (1+r)^n / ((1+r)^n - 1), r = monthly rate. */
    static double emi(double principal, int months, double annualRatePercent) {
        double r = annualRatePercent / 12 / 100;
        double growth = Math.pow(1 + r, months);
        return principal * r * growth / (growth - 1);
    }
}
