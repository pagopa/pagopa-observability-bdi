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

public class CollectPerf02EData {

    @FunctionName("CollectPerf02EDataTimer")
    public void timerTrigger(
        @TimerTrigger(name = "timer", schedule = "%PERF_02E_TIMER_TRIGGER%") String timerInfo, 
        final ExecutionContext context) {
        
        LocalDateTime startDate = LocalDateTime.now().minusHours(1).withMinute(0).withSecond(0);
        LocalDateTime endDate = startDate.plusHours(1);

        context.getLogger().info(String.format("CollectPerf02EData - PERF-02E Timer triggered. Processing interval: %s to %s", startDate, endDate));

        executePerf02EKpi(context, startDate, endDate);
    }

    @FunctionName("CollectPerf02EDataHttp")
    public HttpResponseMessage httpTrigger(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST, HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String inputDate = request.getQueryParameters().get("startDate");
        if (inputDate == null && request.getBody().isPresent()) {
            inputDate = request.getBody().get();
        }

        LocalDateTime startDate;
        LocalDateTime endDate;

        try {
            if (inputDate != null && !inputDate.isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                startDate = LocalDateTime.parse(inputDate, formatter);
            } else {
                startDate = LocalDateTime.now().minusHours(1).withMinute(0).withSecond(0);
            }
            endDate = startDate.plusHours(1);

            context.getLogger().info(String.format("CollectPerf02EData - PERF-02E HTTP triggered. Processing interval: %s to %s", startDate, endDate));

            executePerf02EKpi(context, startDate, endDate);

            return request.createResponseBuilder(HttpStatus.OK)
                .body(String.format("CollectPerf02EData - PERF-02E Processed interval: %s to %s", startDate, endDate))
                .build();
        } catch (Exception e) {
            context.getLogger().severe(String.format("HTTP triggered. Error: %s", e.getMessage()));
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(String.format("CollectPerf02EData - PERF-02E Error processing interval: %s", e.getMessage()))
                .build();
        }
    }

    private void executePerf02EKpi(ExecutionContext context, LocalDateTime startDate, LocalDateTime endDate) {
        PerfKpiService service = PerfKpiService.getInstance(context);

        try {
            service.executePerf02EKpi(startDate, endDate);
        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerf02EData - PERF-02E Error[%s]", e.getMessage()));
        }
    }
}
