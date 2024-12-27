package it.gov.pagopa.observability;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import it.gov.pagopa.observability.service.PerfKpiService;

public class CollectPerfData {

    @FunctionName("CollectPerfDataTimer")
    public void timerTrigger(
        @TimerTrigger(name = "timer", schedule = "%PerfDataTimer%") String timerInfo, 
        final ExecutionContext context) {

        LocalDateTime startDate = LocalDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0);
        LocalDateTime endDate = startDate.plusMonths(1).minusSeconds(1);

        context.getLogger().info(String.format("CollectPerfData - Timer triggered. Processing interval: %s to %s", startDate, endDate));

        executePerf02Kpi(context, startDate, endDate);
    }

    @FunctionName("CollectPerformanceDataHttp")
    public HttpResponseMessage httpTrigger(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST, HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String startInput = request.getQueryParameters().get("startDate");
        String endInput = request.getQueryParameters().get("endDate");

        LocalDateTime startDate;
        LocalDateTime endDate;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

            if (startInput != null && endInput != null) {
                startDate = LocalDateTime.parse(startInput, formatter);
                endDate = LocalDateTime.parse(endInput, formatter);
            } else {
                startDate = LocalDateTime.now()
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0);
                endDate = startDate.plusMonths(1).minusSeconds(1);
            }

            context.getLogger().info(String.format("CollectPerfData - HTTP triggered. Processing interval: %s to %s", startDate, endDate));

            executePerf02Kpi(context, startDate, endDate);

            return request.createResponseBuilder(HttpStatus.OK)
                .body(String.format("CollectPerfData - Processed interval: %s to %s", startDate, endDate))
                .build();

        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerformanceData - HTTP triggered. Error: %s", e.getMessage()));
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(String.format("CollectPerfData - Error processing interval: %s", e.getMessage()))
                .build();
        }
    }

    private void executePerf02Kpi(ExecutionContext context, LocalDateTime startDate, LocalDateTime endDate) {
        PerfKpiService service = PerfKpiService.getInstance(context);

        try {
            service.executePerf02Kpi(startDate, endDate);
        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerfData - PERF-02 Error[%s]", e.getMessage()));
        }
    }
}
