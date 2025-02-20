package it.gov.pagopa.observability;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import it.gov.pagopa.observability.service.PerfKpiService;

public class CollectPerfData {

    @FunctionName("CollectPerfData")
    public HttpResponseMessage httpTrigger(
                @HttpTrigger(name = "req", methods = {HttpMethod.POST}, 
                    authLevel = AuthorizationLevel.ANONYMOUS, route = "perf-data")
                HttpRequestMessage<Optional<String>> request,
                final ExecutionContext context) {

        context.getLogger().info(String.format("CollectPerfData - HTTP triggered, processing input parameters"));

        // get query parameters
        String startDateInput = request.getQueryParameters().get("startDate");
        String endDateInput = request.getQueryParameters().get("endDate");
        String kpiId = Optional.ofNullable(request.getQueryParameters().get("kpiId")).orElse("ALL_KPI");

        try {
            
            // if start date and end date have been specified, that interval is taken into account,
            // otherwise the kpis are calculated about the previous month
            LocalDateTime startDate;
            LocalDateTime endDate;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            if (startDateInput != null && endDateInput != null) {

                startDate = LocalDateTime.parse(startDateInput, formatter);
                endDate = LocalDateTime.parse(endDateInput, formatter);

            } else { // by default the previous month is taken into account
                
                startDate = LocalDateTime.now()
                    .minusMonths(1)
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0);
                endDate = startDate.plusMonths(1).minusSeconds(1);
            }

            context.getLogger().info(String.format("CollectPerfData - Processing interval: %s to %s, kpiId: %s", startDate, endDate, kpiId));
            
            // getting service instance        
            PerfKpiService service = new PerfKpiService();

            // if no kpiId has been specified, all kpis wil be collected
            String rtPerf01  = "0.00";
            String rtPerf02  = "0";
            String rtPerf02E = "0";
            String rtPerf03  = "0";
            String rtPerf04  = "0";
            String rtPerf05  = "0";
            String rtPerf06  = "0";
            String rtOneKpi  = "0";  

            switch (kpiId) {
                case "PERF-01":
                    rtOneKpi = service.executePerf01Kpi(startDate, endDate, context);
                    break;
                case "PERF-02":
                    rtOneKpi = service.executePerf02Kpi(startDate, endDate, context);
                    break;
                case "PERF-02E":
                    // if startDate is not specified then startDate is now minus one hour
                    if (startDateInput != null && !startDateInput.isEmpty()) {
                        startDate = LocalDateTime.parse(startDateInput, formatter);
                    } else {
                        startDate = LocalDateTime.now().minusHours(1).withMinute(0).withSecond(0);
                    }
                    endDate = startDate.plusHours(1);
                    context.getLogger().info(String.format("CollectPerf02EData - PERF-02E HTTP triggered. " +
                        "Processing interval: %s to %s", startDate, endDate));                
                    rtOneKpi = service.executePerf02EKpi(startDate, endDate, context);
                    break;
                case "PERF-03":
                    rtOneKpi = service.executePerfKpi(startDate, endDate, "PERF-03", context);
                    break;
                case "PERF-04":
                    rtOneKpi = service.executePerfKpi(startDate, endDate, "PERF-04", context);
                    break;
                case "PERF-05":
                    rtOneKpi = service.executePerfKpi(startDate, endDate, "PERF-05", context);
                    break;
                case "PERF-06":
                    rtOneKpi = service.executePerfKpi(startDate, endDate, "PERF-06", context);
                    break;
                default: // collect all kpis
                    rtPerf01 = service.executePerf01Kpi(startDate, endDate, context);
                    rtPerf02 = service.executePerf02Kpi(startDate, endDate, context);
                    rtPerf02E = service.executePerf02EKpi(startDate, endDate, context);
                    rtPerf03 = service.executePerfKpi(startDate, endDate, "PERF-03", context);
                    rtPerf04 = service.executePerfKpi(startDate, endDate, "PERF-04", context);
                    rtPerf05 = service.executePerfKpi(startDate, endDate, "PERF-05", context);
                    rtPerf06 = service.executePerfKpi(startDate, endDate, "PERF-06", context);
                    break;
            }
            
            // Build OK response
            context.getLogger().info(String.format("CollectPerfData - Execution completed"));

            // Build response details
            StringBuffer details = new StringBuffer().append("KPI: [").append(kpiId).append("]");
            if ("ALL".equalsIgnoreCase(kpiId)) {
                details.append(" values: [").append(rtPerf01)
                    .append(" | ").append(rtPerf02)
                    .append(" | ").append(rtPerf02E)
                    .append(" | ").append(rtPerf03)
                    .append(" | ").append(rtPerf04)
                    .append(" | ").append(rtPerf05)
                    .append(" | ").append(rtPerf06)
                    .append("]");
            } else {
                details.append(" value: [").append(rtOneKpi).append("]");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("status", String.valueOf(HttpStatus.OK));
            rootNode.put("message", String.format("Processed interval: %s to %s", startDate, endDate));
            rootNode.put("details", details.toString());
            String responseBody = objectMapper.writeValueAsString(rootNode);
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(String.format("CollectPerfData - HTTP triggered. " +
                    "Error: %s", e.getMessage()));

            // Build KO response
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("status", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR));
            rootNode.put("message", String.format("CollectPerformanceData - HTTP triggered. Error: %s", e.getMessage()));
            rootNode.put("details", String.format("CollectPerfData - Error: %s ", e.getMessage()));

            try {
                String responseBody = objectMapper.writeValueAsString(rootNode);
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body(responseBody)
                        .build();
            } catch (JsonProcessingException jpe) {
                context.getLogger().severe("CollectPerformanceData - HTTP triggered. " +
                        "Error while serializing error response");
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body(String.format("CollectPerfData - generic error during elaboration: %s",
                                jpe.getMessage()))
                        .build();
            }
        }
    }
}
