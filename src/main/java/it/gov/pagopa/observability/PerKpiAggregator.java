package it.gov.pagopa.observability;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientFactory;
import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.KustoResultSetTable;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;

import it.gov.pagopa.observability.models.PerfDataResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.fasterxml.jackson.databind.ObjectMapper;



public class PerKpiAggregator {

    @FunctionName("PerKpiAggregatorTimer")
    public void run(
            @TimerTrigger(name = "timer", schedule = "0 0 2 5 1,4,7,10 *") String timerInfo,
            final ExecutionContext context) {

        context.getLogger().info("QuarterlyKpiAggregator triggered.");

        try {
            // Recupera le date per i tre mesi precedenti
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime firstMonth = now.minusMonths(3).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime secondMonth = now.minusMonths(2).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime thirdMonth = now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

            String firstMonthString = queryKpiAverages(firstMonth, firstMonth.plusMonths(1).minusSeconds(1), context);
            String secondMonthString = queryKpiAverages(secondMonth, secondMonth.plusMonths(1).minusSeconds(1), context);
            String thirdMonthString = queryKpiAverages(thirdMonth, thirdMonth.plusMonths(1).minusSeconds(1), context);

            // Scrive i dati su Event Hub
            sendToEventHub(firstMonthString, context);
            sendToEventHub(secondMonthString, context);
            sendToEventHub(thirdMonthString, context);

        } catch (Exception e) {
            context.getLogger().severe("Error occurred in QuarterlyKpiAggregator: " + e.getMessage());
        }
    }

    @FunctionName("PerKpiAggregatorHttp")
    public HttpResponseMessage httpTrigger(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST, HttpMethod.GET}, 
            authLevel = AuthorizationLevel.FUNCTION, route = "perfKpi/")
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        try {
            
        } catch (Exception e) {
            // TODO: handle exception
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
    }

    private String queryKpiAverages(LocalDateTime startDate, LocalDateTime endDate, ExecutionContext context) throws Exception {
        String query = String.format(
                "let start = datetime('%s'); let end = datetime('%s'); " +
                        "['%s'] | summarize avg_PERF01=avg(PERF01), " + 
                                        "avg_PERF02=avg(PERF-01), " +
                                        "avg_PERF02=avg(PERF-02), " +
                                        "avg_PERF02E=avg(PERF-02E), " +
                                        "avg_PERF03=avg(PERF-03), " +
                                        "avg_PERF04=avg(PERF-04), " +
                                        "avg_PERF05=avg(PERF-05), " +
                                        "avg_PERF06=avg(PERF-06) " +
                        "| project avg_PERF01, avg_PERF02, avg_PERF02E,  avg_PERF03, avg_PERF04, avg_PERF05, avg_PERF06",
                startDate, endDate, System.getenv("ADX_PERF_TABLE")
        );

        ConnectionStringBuilder csb = ConnectionStringBuilder.createWithAadManagedIdentity(
                System.getenv("ADX_CLUSTER_URL"), System.getenv("ADX_DATABASE_NAME"));
        Client client = ClientFactory.createClient(csb);

        KustoOperationResult result = client.execute(System.getenv("ADX_DATABASE_NAME"), query);
        KustoResultSetTable resultSet = result.getPrimaryResults();

        if (resultSet.next()) {
            double avg_PERF01 = resultSet.getDouble("avg_PERF01");
            double avg_PERF02 = resultSet.getDouble("avg_PERF02");
            double avg_PERF02E = resultSet.getDouble("avg_PERF02E");
            double avg_PERF03 = resultSet.getDouble("avg_PERF03");
            double avg_PERF04 = resultSet.getDouble("avg_PERF04");
            double avg_PERF05 = resultSet.getDouble("avg_PERF05");
            double avg_PERF06 = resultSet.getDouble("avg_PERF06");

            return String.format("%s,%s,%s,%s,%s,%s,%s",
                    avg_PERF01, avg_PERF02, avg_PERF02E, avg_PERF03, avg_PERF04, avg_PERF05, avg_PERF06);
        } else {
            context.getLogger().warning("No data found for query.");
            return "0.0,0.0,0.0,0.0,0.0,0.0,0.0"; // Default values
        }
    }

    private void sendToEventHub(String message, ExecutionContext context) {
        context.getLogger().info("Sending data to Event Hub: " + message);

        try {
            String fullyQualifiedNamespace = System.getenv("EVENT_HUB_NAMESPACE");
            String eventHubName = System.getenv("EVENT_HUB_NAME");

            EventHubProducerClient producer = new EventHubClientBuilder()
                .credential(fullyQualifiedNamespace, eventHubName, new DefaultAzureCredentialBuilder().build())
                .buildProducerClient();

            // Create a batch to send the message
            EventData eventData = new EventData(message);
            producer.createBatch().tryAdd(eventData);
            producer.send(producer.createBatch());
            context.getLogger().info("Data successfully sent to Event Hub.");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}