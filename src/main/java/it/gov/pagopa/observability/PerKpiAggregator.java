package it.gov.pagopa.observability;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import it.gov.pagopa.observability.service.PerfKpiService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class PerKpiAggregator {

    @FunctionName("PerKpiAggregator")
    public HttpResponseMessage httpTrigger(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, 
                authLevel = AuthorizationLevel.ANONYMOUS, route = "quarter/{quarter}")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("quarter") String quarter, 
            final ExecutionContext context) {

        context.getLogger().info(String.format("PerKpiAggregator - HTTP triggered, processing input parameters"));
        
        // if year query param is not specified, the current year is taken into account
        String year = request.getQueryParameters().get("year");
        if (year == null || year.isEmpty()) {
            year = String.valueOf(LocalDateTime.now().getYear());
        }

        // calculating the qaurter to compute
        String firstMonthString = "";
        String secondMonthString = "";
        String thirdMonthString = "";
        try {
            List<String> quarters = Arrays.asList("q1", "q2", "q3", "q4", "last");
            if (quarters.stream().noneMatch(s -> s.equalsIgnoreCase(quarter))) {
                throw new Exception("quarter parm must be one of 'Q1', 'Q2', 'Q3', 'Q4' or 'LAST'");
            }

            LocalDateTime firstMonth;
            LocalDateTime secondMonth;
            LocalDateTime thirdMonth;

            if ("last".equalsIgnoreCase(quarter)) { // get the last quarter
                LocalDateTime now = LocalDateTime.now();
                firstMonth = now.minusMonths(3).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                secondMonth = now.minusMonths(2).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                thirdMonth = now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

            } else { // get specific quarter, year matters

                LocalDateTime now = LocalDateTime.of(Integer.parseInt(year), 1, 1,0,0);
                int offset = 0;
                switch (quarter.toLowerCase()) {
                    case "q1":
                        offset += 2;
                        break;
                    case "q2":
                        offset += 5;
                        break;
                    case "q3":
                        offset += 8;
                        break;
                    case "q4":
                        offset += 11;
                        break;
                }

                firstMonth = now.plusMonths(offset).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                secondMonth = now.plusMonths((offset - 1)).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                thirdMonth = now.plusMonths((offset - 2)).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            }

            context.getLogger().info(String.format("PerKpiAggregator - Calculating kpis for quarter [%s] year [%s]", quarter, year));
            
            PerfKpiService service = new PerfKpiService();
            firstMonthString = service.queryKpiAverages(firstMonth, firstMonth.plusMonths(1).minusSeconds(1), context);
            secondMonthString = service.queryKpiAverages(secondMonth, secondMonth.plusMonths(1).minusSeconds(1), context);
            thirdMonthString = service.queryKpiAverages(thirdMonth, thirdMonth.plusMonths(1).minusSeconds(1), context);
            
            context.getLogger().info(String.format("PerKpiAggregator - kpis calculated"));
            
            // building message to send to evh
            List<String> data = List.of(
                firstMonthString,
                secondMonthString,
                thirdMonthString
                );
                
            // Serialize to JSON and send to evh
            String jsonString = serializeToJson(year, quarter, data);
            context.getLogger().info(String.format("PerKpiAggregator - Sending kpis to evh"));
            service.sendToEventHub(jsonString, context);

            // Build OK response
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("status", String.valueOf(HttpStatus.OK));
            rootNode.put("message", String.format("PerKpiAggregator - Processed quarter %s/%s", year, quarter));

            ArrayNode dataArray = objectMapper.createArrayNode();
            for (String record : data) {
                dataArray.add(record);
            }
            rootNode.set("data", dataArray);
            String responseBody = objectMapper.writeValueAsString(rootNode);

            context.getLogger().info(String.format("PerKpiAggregator - Execution completed"));

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(responseBody)
                    .build();

        } catch (Exception e) {

            context.getLogger().severe(String.format("PerKpiAggregator - error while quarter %s elaboration: %s"
                    ,quarter, e.getMessage()));

            // Build KO response
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("message", String.format("PerKpiAggregator - error while quarter %s elaboration", quarter));
            rootNode.put("details", String.format("Error: %s", e.getMessage()));
            try {
                String responseBody = objectMapper.writeValueAsString(rootNode);
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body(responseBody)
                        .build();
            } catch (Exception ex) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while quarter %s elaboration: %s"
                        ,quarter, ex.getMessage()));
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body(String.format("PerKpiAggregator - error while quarter %s elaboration: %s"
                                ,quarter, ex.getMessage()))
                        .build();
            }
        }
    }

    public static String serializeToJson(String year, String quarter, List<String> data) throws Exception {

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String createDate = now.format(formatter);

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode rootNode = objectMapper.createArrayNode();
        ObjectNode dataNode;
        for (String record : data) {
            dataNode = objectMapper.createObjectNode();
            dataNode.put("create_date", createDate);
            dataNode.put("year", year);
            dataNode.put("quarter", quarter);

            String[] kpis = record.split(",");
            dataNode.put("PERF-01", kpis[0]);
            dataNode.put("PERF-02", kpis[1]);
            dataNode.put("PERF-02E", kpis[2]);
            dataNode.put("PERF-03", kpis[3]);
            dataNode.put("PERF-04", kpis[4]);
            dataNode.put("PERF-05", kpis[5]);
            dataNode.put("PERF-06", kpis[6]);

            rootNode.add(dataNode);
        }

        return objectMapper.writeValueAsString(rootNode);
    }
}