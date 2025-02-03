package it.gov.pagopa.observability.service;

import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientFactory;
import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.KustoResultSetTable;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.ingest.IngestClient;
import com.microsoft.azure.kusto.ingest.IngestClientFactory;
import com.microsoft.azure.kusto.ingest.IngestionProperties;
import com.microsoft.azure.kusto.ingest.source.StreamSourceInfo;
import it.gov.pagopa.observability.helper.PerfKpiHelper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PerfKpiService {

    private static PerfKpiService instance;

    private final String databaseName;
    private final String ADX_SOURCE_TABLE;
    private final String ADX_PERF_TABLE;
    private final String APP_INSIGHTS_API_URL;
    private final ExecutionContext CONTEXT;
    private final String BETTERSTACK_API_URL;
    private final String BETTERSTACK_API_KEY;
    private final String APP_INSIGHTS_API_KEY;

    private PerfKpiService(ExecutionContext context) {
        
        this.databaseName = System.getenv("ADX_DATABASE_NAME");
        this.ADX_SOURCE_TABLE = System.getenv("ADX_SOURCE_TABLE");
        this.ADX_PERF_TABLE = System.getenv("ADX_PERF_TABLE");
        this.APP_INSIGHTS_API_URL = System.getenv("AAI_API_URL");
        this.BETTERSTACK_API_URL = System.getenv("BETTERSTACK_API_URL");
        this.CONTEXT = context;
        
        // get betterstack api key from keyvault
        String keyVaultUrl = System.getenv("KEYVAULT_URI");
        SecretClient secretClient = new SecretClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        String bsSecretName = System.getenv("BETTERSTACK_API_KEY");
        String bsSecretValue = "";
        String aiSecretName = System.getenv("APP_INSIGHTS_API_KEY");
        String aiSecretValue = "";
        try {
            bsSecretValue = secretClient.getSecret(bsSecretName).getValue();
            aiSecretValue = secretClient.getSecret(aiSecretName).getValue();
        } catch (Exception e) {
            context.getLogger().severe(String.format("PerfKpiService - Error while retrieving BETTERSTACK_API_KEY or APP_INSIGHTS_API_KEY " +
                    "Error: %s", e.getMessage()));
            throw e;
        }

        this.BETTERSTACK_API_KEY = bsSecretValue;
        this.APP_INSIGHTS_API_KEY = aiSecretValue;


        
        context.getLogger().info(String.format("|GetPerformanceKpiService|Database Name[%s] Source table[%s] Destination table[%s]", 
            databaseName, ADX_SOURCE_TABLE, ADX_PERF_TABLE));
    }

    public static synchronized PerfKpiService getInstance(ExecutionContext context) {
        if (instance == null) {
            instance = new PerfKpiService(context);
        }
        return instance;
    } 

    public int executePerf02Kpi(LocalDateTime startDate, LocalDateTime endDate) throws Exception {

        String perf02Query = String.format(
            "let start = datetime(%s); " +
            "let end = datetime(%s); " +
            "%s | where insertedTimestamp between (start .. end) " +
            "| where sottoTipoEvento == 'REQ' " +
            "| where categoriaEvento == 'INTERFACCIA' " +
            "| summarize count=count()" + 
            "| project count",
            startDate, endDate, ADX_SOURCE_TABLE
        );
        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();
        Client kustoClient = ClientFactory.createClient(csb);
        KustoOperationResult result = kustoClient.execute(databaseName, perf02Query);
        int count = 0;
        KustoResultSetTable resultSet = null;
        if (result.hasNext()) {
            resultSet = result.getPrimaryResults();
            if (resultSet.next()) {
                count = resultSet.getInt("count");
            }
        }
        CONTEXT.getLogger().info(String.format("PerformanceKpiService - " +
                "PERF-02 Query Result[%s] startDate[%s] endDate[%s]", count, startDate, endDate));

        // insert the result into the destination table
        writePerfKpiData(databaseName, ADX_PERF_TABLE, startDate, endDate, "PERF-02", Integer.toString(count));
        CONTEXT.getLogger().info(String.format("PerformanceKpiService - PERF-02 Query Result[%s] startDate[%s] endDate[%s]", count, startDate, endDate));
        return count;
    }

    public int executePerf02EKpi(LocalDateTime startDate, LocalDateTime endDate) throws Exception {

        String perf0E2Query = String.format(
            "let start = datetime(%s);" + 
            "let end = datetime(%s);" + 
            "%s" + 
            "| where insertedTimestamp between (start .. end)" + 
            "      and sottoTipoEvento == 'RESP'" + 
            "      and categoriaEvento == 'INTERFACCIA'" + 
            "      and tipoEvento !in ('cdInfoWisp', 'mod3CancelV2', 'mod3CancelV1', 'parkedList-v1')" + 
            "      and isnotempty(payload)" + 
            "| extend payloadDec = base64_decode_tostring(payload)" + 
            "| where payloadDec contains 'faultCode'" + 
            "| summarize count=count()" + 
            "| project count",
            startDate, endDate, ADX_SOURCE_TABLE
        );

        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();
        Client kustoClient = ClientFactory.createClient(csb);
        KustoOperationResult result = kustoClient.execute(databaseName, perf0E2Query);
        int count = 0;
        KustoResultSetTable resultSet = null;
        if (result.hasNext()) {
            resultSet = result.getPrimaryResults();
            if (resultSet.next()) {
                count = resultSet.getInt("count");
            }
        }
        CONTEXT.getLogger().info(String.format("PerformanceKpiService - PERF-02E Query Result[%s] startDate[%s] endDate[%s]", count, startDate, endDate));

        writePerfKpiData(databaseName, ADX_PERF_TABLE, startDate, endDate, "PERF-02E", Integer.toString(count));

        return count;
    }

    public double executePerfKpi(LocalDateTime startDate, LocalDateTime endDate, String kpiId) throws Exception {

        try {
            CONTEXT.getLogger().info(String.format("PerformanceKpiService - %s calculating KPI for period: %s to %s", kpiId, startDate, endDate));

            String cloudRoleName = System.getenv("CLOUD_ROLE_NAME");
            String operationName = System.getenv(kpiId + "_OPERATION_NAME");

            // Query Application Insights
            String query = String.format(
                "requests | where timestamp between (todatetime('%s') .. todatetime('%s')) "
                    + "| where cloud_RoleName == '%s' "
                    //+ "| where name in ('POST /nodo-auth/node-for-psp/v1', 'POST /nodo-auth/node-for-psp/V1/') "
                    + "| where operation_Name == '%s' "
                    + "| summarize avg = avg(duration)"
                    + "| project avg",
                startDate.toString(), endDate.toString(), cloudRoleName, operationName
            );

            String avgDuration = executeAppInsightsQuery(query, kpiId);

            //avgDuration = Math.round(avgDuration * 100.0) / 100.0;
            writePerfKpiData(databaseName, ADX_PERF_TABLE, startDate, endDate, kpiId, avgDuration);
            CONTEXT.getLogger().info("KPI record successfully inserted into Azure Table.");

            return Double.valueOf(avgDuration);

        } catch (Exception e) {
            CONTEXT.getLogger().severe(String.format("PerformanceKpiService - %s Error executing KPI calculation: %s", kpiId, e.getMessage()));
            throw e;
        }
    }

    public void executePerf01Kpi(LocalDateTime startDate, LocalDateTime endDate) throws Exception {

        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String fromDate = startDate.format(FORMATTER);
        String toDate = endDate.format(FORMATTER);

        // Building api url and http request
        String url = String.format("%s?from=%s&to=%s", BETTERSTACK_API_URL, fromDate, toDate);
        HttpClient client = HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + BETTERSTACK_API_KEY)
            .GET()
            .build();

        // Calliing api
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Parse json response
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.body());
            String availabilty = rootNode.path("data")
                           .path("attributes")
                           .path("availability")
                           .asText();
            
            // write kpi to db
            writePerfKpiData(databaseName, ADX_PERF_TABLE, startDate, endDate, "PERF-01", availabilty);

        } else {
            throw new RuntimeException(String.format("PerformanceKpiService - %s Error executing KPI calculation: %s - %s",
            "PERF-01", response.statusCode(), response.body()));
        }
    }    

    /**
     * Execute a query on Application Insights.
     * @param query    Query Kusto to execute
     * @param kpiId    KPI id to compute
     * @return         the value for the specified kpi
     * @throws Exception in case of error
     */
    public String executeAppInsightsQuery(String query, String kpiId) throws Exception {

        if (APP_INSIGHTS_API_URL == null || APP_INSIGHTS_API_URL.isEmpty() || APP_INSIGHTS_API_KEY == null || APP_INSIGHTS_API_KEY.isEmpty()) {
            throw new IllegalArgumentException("API URL o API Key non possono essere nulli o vuoti.");
        }

        String body = String.format("{\"query\": \"%s\"}", query);

        HttpRequest request = new HttpRequest(HttpMethod.POST, APP_INSIGHTS_API_URL)
                .setHeader("Content-Type", "application/json")
                .setHeader("x-api-key", APP_INSIGHTS_API_KEY)
                .setBody(body);

        // Creazione dell'HttpClient
        com.azure.core.http.HttpClient httpClient = com.azure.core.http.HttpClient.createDefault();
        HttpPipeline httpPipeline = new HttpPipelineBuilder()
                .httpClient(httpClient)
                .policies(new RetryPolicy())  // Aggiunge retry automatici in caso di failure transitoria
                .build();

        // Esecuzione della richiesta HTTP
        HttpResponse response = httpPipeline.send(request).block();

        if (response != null) {
            String responseBody = response.getBodyAsString().block();
            return extractValueFromResponse(responseBody, kpiId);
        } else {
            System.err.println(String.format("PerformanceKpiService - %s No response received from Application Insights query", kpiId));
            return "0";
        }
    }

    /**
     * Extracts the value from the json object returned by Application Insights.
     *
     * @param responseBody Application Insights json response
     * @param kpiId        KPI id
     * @return             the value calculated by the query, or "0" if empty
     * @throws Exception in case of error
     */
    private String extractValueFromResponse(String responseBody, String kpiId) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode avgNode = rootNode.path("tables").get(0).path("rows").get(0);

        if (!avgNode.isMissingNode()) {
            return avgNode.get(0).asText();
        } else {
            System.err.println(String.format("PerformanceKpiService - %s No result received from Application Insights query", kpiId));
            return "0";
        }
    }

    private void writePerfKpiData(String databaseName, 
                                    String tableName, 
                                    LocalDateTime startDate, 
                                    LocalDateTime endDate, 
                                    String kpiName, String kpiValue) throws Exception{

        // formatting date
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String data = String.format(
            ".ingest inline into table %s <| %s,%s,%s,%s,%s",
            ADX_PERF_TABLE,
            now,
            startDate.format(formatter),
            endDate.format(formatter),
            kpiName,
            kpiValue
        );

        // prepare ingestion
        ByteArrayInputStream ingestQueryStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        IngestClient ingestClient = IngestClientFactory.createClient(PerfKpiHelper.getConnectionStringBuilder());
        IngestionProperties ingestionProperties = new IngestionProperties(databaseName, ADX_PERF_TABLE);
        ingestionProperties.setDataFormat(IngestionProperties.DataFormat.CSV);
        StreamSourceInfo sourceInfo = new StreamSourceInfo(ingestQueryStream);

        // execute ingestion
        ingestClient.ingestFromStream(sourceInfo, ingestionProperties);
        ingestClient.close();
        CONTEXT.getLogger().info(String.format("PerformanceKpiService - %s Data successfully inserted into[%s]", kpiName, ADX_PERF_TABLE));
    }

    public String queryKpiAverages(LocalDateTime startDate, LocalDateTime endDate, ExecutionContext context) throws Exception {
        String query = String.format(
                "let start = datetime('%s');" +
                "let end = datetime('%s');" +
                "%s" +
                "| where start >= startDate and end <= endDate" +
                "| summarize " +
                "    avg_PERF01 = avgif(kpiValue, kpiId contains \"PERF-01\")," +
                "    avg_PERF02 = floor(avgif(kpiValue, kpiId contains \"PERF-02\"), 1)," +
                "    avg_PERF02E = floor(avgif(kpiValue, kpiId contains \"PERF-02E\"), 1)," +
                "    avg_PERF03 = floor(avgif(kpiValue, kpiId contains \"PERF-03\"), 1)," +
                "    avg_PERF04 = floor(avgif(kpiValue, kpiId contains \"PERF-04\"), 1)," +
                "    avg_PERF05 = floor(avgif(kpiValue, kpiId contains \"PERF-05\"), 1)," +
                "    avg_PERF06 = floor(avgif(kpiValue, kpiId contains \"PERF-06\"), 1)" +
                "| project avg_PERF01, avg_PERF02, avg_PERF02E, avg_PERF03, avg_PERF04, avg_PERF05, avg_PERF06",
                startDate, endDate, System.getenv("ADX_PERF_TABLE")
        );
  
        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();
        Client client = ClientFactory.createClient(csb);

        KustoOperationResult result = client.execute(System.getenv("ADX_DATABASE_NAME"), query);
        KustoResultSetTable resultSet = result.getPrimaryResults();
        if (resultSet.next()) {
            String avg_PERF01 = "100.00";
            try {
                avg_PERF01 = resultSet.getString("avg_PERF01");
                double perf01d = Double.valueOf(avg_PERF01).doubleValue();
                DecimalFormat df = new DecimalFormat("#.00");
                avg_PERF01 = df.format(perf01d);
            } catch (Exception e) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while getting avg_PERF01 from resultset: %s",
                e.getMessage()));
            }
            String avg_PERF02 = "0"; 
            try {
                avg_PERF02 = resultSet.getString("avg_PERF02");
            } catch (Exception e) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while getting avg_PERF02 from resultset: %s",
                e.getMessage()));
            }
            String avg_PERF02E = "0"; 
            try {
                avg_PERF02E = resultSet.getString("avg_PERF02E");
            } catch (Exception e) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while getting avg_PERF02E from resultset: %s",
                e.getMessage()));
            }
            String avg_PERF03 = "0"; 
            try {
                avg_PERF03 = resultSet.getString("avg_PERF03");
            } catch (Exception e) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while getting avg_PERF03 from resultset: %s",
                e.getMessage()));
            }
            String avg_PERF04 = "0"; 
            try {
                avg_PERF04 = resultSet.getString("avg_PERF04");
            } catch (Exception e) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while getting avg_PERF04 from resultset: %s",
                e.getMessage()));
            }
            String avg_PERF05 = "0"; 
            try {
                avg_PERF05 = resultSet.getString("avg_PERF05");
            } catch (Exception e) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while getting avg_PERF05 from resultset: %s",
                e.getMessage()));
            }
            String avg_PERF06 = "0"; 
            try {
                avg_PERF06 = resultSet.getString("avg_PERF06");
            } catch (Exception e) {
                context.getLogger().severe(String.format("PerKpiAggregator - error while getting avg_PERF06 from resultset: %s",
                e.getMessage()));
            }

            return String.format("%s,%s,%s,%s,%s,%s,%s",
                    avg_PERF01, avg_PERF02, avg_PERF02E, avg_PERF03, avg_PERF04, avg_PERF05, avg_PERF06);
        } else {
            context.getLogger().warning("No data found for query.");
            return "0.0,0.0,0.0,0.0,0.0,0.0,0.0"; // Default values
        }
    }

    public void sendToEventHub(String message, ExecutionContext context) throws Exception {

        context.getLogger().severe(String.format("PerKpiAggregator - sending data to evh: %s", message));

        try {
            String fullyQualifiedNamespace = System.getenv("EVENT_HUB_NAMESPACE");
            String eventHubName = System.getenv("EVENT_HUB_NAME");

            if (fullyQualifiedNamespace == null || eventHubName == null) {
                throw new IllegalArgumentException("Environment variables EVENT_HUB_NAMESPACE or EVENT_HUB_NAME are not set.");
            }

            EventHubProducerClient producer = new EventHubClientBuilder()
                .credential(fullyQualifiedNamespace, eventHubName, new DefaultAzureCredentialBuilder().build())
                .buildProducerClient();

            // Create a batch to send the message
            EventDataBatch batch = producer.createBatch();
            EventData eventData = new EventData(message);

            if (!batch.tryAdd(eventData)) {
                throw new IllegalStateException("Event data is too large to fit in the batch.");
            }

            producer.send(batch);
            context.getLogger().info("Data successfully sent to Event Hub.");

        } catch (Exception e) {
            context.getLogger().severe(String.format("Error while sending data to Event Hub: %s", e.getMessage()));
            throw e;
        }

    }
}
