package it.gov.pagopa.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.observability.models.PerfDataResponse;
import it.gov.pagopa.observability.service.PerfKpiService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class CollectPerfData {

    @FunctionName("CollectPerfDataTimer")
    public void timerTrigger(
        @TimerTrigger(name = "timer", schedule = "%PERF_DATA_TIMER_TRIGGER%") String timerInfo, 
        final ExecutionContext context) {

        LocalDateTime startDate = LocalDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0);
        LocalDateTime endDate = startDate.plusMonths(1).minusSeconds(1);

        context.getLogger().info(String.format("CollectPerfData - Timer triggered. Processing interval: %s to %s", startDate, endDate));

        executePerf02Kpi(context, startDate, endDate);
        executePerf03Kpi(context, startDate, endDate);
        executePerf04Kpi(context, startDate, endDate);
        executePerf05Kpi(context, startDate, endDate);
        executePerf06Kpi(context, startDate, endDate);
    }

    @FunctionName("CollectPerfDataHttp")
    public HttpResponseMessage httpTrigger(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST, HttpMethod.GET}, 
            authLevel = AuthorizationLevel.FUNCTION, route = "collectPerfData")
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String startDateInput = request.getQueryParameters().get("startDate");
        String endDateInput = request.getQueryParameters().get("endDate");
        String kpiId = Optional.ofNullable(request.getQueryParameters().get("kpiId")).orElse("ALL_KPI");

        LocalDateTime startDate;
        LocalDateTime endDate;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

            if (startDateInput != null && endDateInput != null) {
                startDate = LocalDateTime.parse(startDateInput, formatter);
                endDate = LocalDateTime.parse(endDateInput, formatter);
            } else {
                startDate = LocalDateTime.now()
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0);
                endDate = startDate.plusMonths(1).minusSeconds(1);
            }

            context.getLogger().info(String.format("CollectPerfData - HTTP triggered. Processing interval: %s to %s", startDate, endDate));

            switch (kpiId) {
                case "PERF-02":
                    executePerf02Kpi(context, startDate, endDate);
                    break;
                case "PERF-03":
                    executePerf03Kpi(context, startDate, endDate);
                    break;
                case "PERF-04":
                    executePerf04Kpi(context, startDate, endDate);
                    break;
                case "PERF-05":
                    executePerf05Kpi(context, startDate, endDate);
                    break;
                case "PERF-06":
                    executePerf06Kpi(context, startDate, endDate);
                    break;
                
                default: // ALL_KPI
                        executePerf02Kpi(context, startDate, endDate);
                        executePerf03Kpi(context, startDate, endDate);
                        executePerf04Kpi(context, startDate, endDate);
                        executePerf05Kpi(context, startDate, endDate);
                        executePerf06Kpi(context, startDate, endDate);
                        break;
            }
            
            // Build OK response
            String message = String.format("CollectPerfData - Processed interval: %s to %s", startDate, endDate);
            PerfDataResponse response = new PerfDataResponse(String.valueOf(HttpStatus.OK), message);
            ObjectMapper objectMapper = new ObjectMapper();
            String responseBody = objectMapper.writeValueAsString(response);
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerformanceData - HTTP triggered. Error: %s", e.getMessage()));
            // Build KO response
            String message = String.format("CollectPerformanceData - HTTP triggered. Error: %s", e.getMessage());
            PerfDataResponse response = new PerfDataResponse(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR), message);
            ObjectMapper objectMapper = new ObjectMapper();
            String responseBody = "CollectPerfData - Generic error has occurred: " + e.getMessage();
            try {
                responseBody = objectMapper.writeValueAsString(response);
            } catch (JsonProcessingException e1) {
                context.getLogger().severe("CollectPerformanceData - HTTP triggered. Error while serializing error response");
                e1.printStackTrace();
            }
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(responseBody)
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

    private void executePerf03Kpi(ExecutionContext context, LocalDateTime startDate, LocalDateTime endDate) {
        PerfKpiService service = PerfKpiService.getInstance(context);
        try {
            service.executePerfKpi(startDate, endDate, "PERF-03");
        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerfData - PERF-03 Error[%s]", e.getMessage()));
        } 
    }

    private void executePerf04Kpi(ExecutionContext context, LocalDateTime startDate, LocalDateTime endDate) {
        PerfKpiService service = PerfKpiService.getInstance(context);
        try {
            service.executePerfKpi(startDate, endDate, "PERF-04");
        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerfData - PERF-04 Error[%s]", e.getMessage()));
        } 
    }

    private void executePerf05Kpi(ExecutionContext context, LocalDateTime startDate, LocalDateTime endDate) {
        PerfKpiService service = PerfKpiService.getInstance(context);
        try {
            service.executePerfKpi(startDate, endDate, "PERF-05");
        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerfData - PERF-05 Error[%s]", e.getMessage()));
        } 
    }

    private void executePerf06Kpi(ExecutionContext context, LocalDateTime startDate, LocalDateTime endDate) {
        PerfKpiService service = PerfKpiService.getInstance(context);
        try {
            service.executePerfKpi(startDate, endDate, "PERF-06");
        } catch (Exception e) {
            context.getLogger().severe(String.format("CollectPerfData - PERF-06 Error[%s]", e.getMessage()));
        } 
    }
}
