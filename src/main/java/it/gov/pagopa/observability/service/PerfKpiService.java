package it.gov.pagopa.observability.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.azure.core.credential.AzureNamedKeyCredential;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
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

public class PerfKpiService {

    private String ADX_DB_NAME;
    private String ADX_SOURCE_TABLE;
    private String ADX_PERF_TABLE;
    private String APP_INSIGHTS_API_URL;
    private String BETTERSTACK_API_URL;
    private String BETTERSTACK_API_KEY;
    private String APP_INSIGHTS_API_KEY;
    private String CLOUD_ROLE_NAME;
    private String EVENT_HUB_NAME;
    private String EVENT_HUB_NAMESPACE;
    private String EVENT_HUB_KEY_NAME;
    private String EVENT_HUB_KEY;

    public PerfKpiService() {

        
        this.ADX_DB_NAME = System.getenv("ADX_DATABASE_NAME");
    
        if (this.ADX_DB_NAME == null || this.ADX_DB_NAME.isEmpty()) {
            this.ADX_DB_NAME = System.getProperty("ADX_DATABASE_NAME", "default_test_db"); // Usa System Property nei test
        }
    
        if (this.ADX_DB_NAME.equals("default_test_db")) {
            System.out.println("⚠️ PerfKpiService - ADX_DATABASE_NAME impostato con un valore di test.");
        }
        this.ADX_SOURCE_TABLE = System.getenv("ADX_SOURCE_TABLE");
        this.ADX_PERF_TABLE = System.getenv("ADX_PERF_TABLE");
        this.APP_INSIGHTS_API_URL = System.getenv("APP_INSIGHTS_API_URL");
        this.BETTERSTACK_API_URL = System.getenv("BETTERSTACK_API_URL");
        this.BETTERSTACK_API_KEY = System.getenv("BETTERSTACK_API_KEY");
        this.APP_INSIGHTS_API_KEY = System.getenv("APP_INSIGHTS_API_KEY");
        this.CLOUD_ROLE_NAME = System.getenv("CLOUD_ROLE_NAME");
        this.EVENT_HUB_NAME = System.getenv("EVENT_HUB_NAME");
        this.EVENT_HUB_NAMESPACE = System.getenv("EVENT_HUB_NAMESPACE");
        this.EVENT_HUB_KEY_NAME = System.getenv("EVENT_HUB_KEY_NAME");
        this.EVENT_HUB_KEY = System.getenv("EVENT_HUB_KEY");
    }

    /**
     * Computes PERF-02 kpi (Number of messages managed by the platform)
     * Performs a query on ADX ReEvent DB 
     * @param startDate date from in the query
     * @param endDate date end in the query
     * @param context Azure function context
     * @return the number of total messages
     * @throws Exception
     */
    public int executePerf02Kpi(LocalDateTime startDate, LocalDateTime endDate, ExecutionContext context) throws Exception {

        
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
        KustoOperationResult result = kustoClient.executeQuery(ADX_DB_NAME, perf02Query);
        int count = 0;
        KustoResultSetTable resultSet = null;
        if (result.hasNext()) {
            resultSet = result.getPrimaryResults();
            if (resultSet.next()) {
                count = resultSet.getInt("count");
            }
        }

        context.getLogger().info(String.format("executePerf02Kpi - " +
                "PERF-02 Query Result[%s] startDate[%s] endDate[%s]", count, startDate, endDate));

        // insert the result into the destination table
        if (System.getProperty("ENVIRONMENT") == null || "TEST".equalsIgnoreCase(System.getProperty("ENVIRONMENT"))) {
            writePerfKpiData(startDate, endDate, "PERF-02", Integer.toString(count), context);
        }

        return count;
    }

    /**
     * Computes PERF-02E kpi (Number of messages in error managed by the platform)
     * Performs a query on ADX ReEvent DB
     * WARNING - the adx query is very heavy, the date rage has to be short (max 2 hours)
     * @param startDate date from in the query
     * @param endDate date to in the query
     * @param context Azure function context
     * @return toal number of messages in error
     * @throws Exception
     */
    public int executePerf02EKpi(LocalDateTime startDate, LocalDateTime endDate, ExecutionContext context) throws Exception {

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
    
        // Create connection and client
        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();
        Client kustoClient = ClientFactory.createClient(csb);

        // Execute query
        KustoOperationResult result = kustoClient.executeQuery(ADX_DB_NAME, perf0E2Query);
        int count = 0;
        KustoResultSetTable resultSet = null;
        if (result.hasNext()) {
            resultSet = result.getPrimaryResults();
            if (resultSet.next()) {
                count = resultSet.getInt("count");
            }
        }

        context.getLogger().info(String.format("executePerf02EKpi - PERF-02E Query Result[%s] startDate[%s] endDate[%s]", count, startDate, endDate));

        writePerfKpiData(startDate, endDate, "PERF-02E", Integer.toString(count), context);
        return count;
    }

    /**
     * Computes the response time of some NDP primitives (PERF-03, PERF-04, PERF-05 e PERF-06)
     * by execution of a app insights query
     * @param startDate date from in the query
     * @param endDate date to in the query
     * @param kpiId kpiId to calculate
     * @param context Azure function context
     * @return
     * @throws Exception
     */
    public double executePerfKpi(LocalDateTime startDate, LocalDateTime endDate, String kpiId, ExecutionContext context) throws Exception {

        try {

            context.getLogger().info(String.format("executePerfKpi - %s calculating KPI for period: %s to %s", kpiId, startDate, endDate));

            APP_INSIGHTS_API_KEY = (APP_INSIGHTS_API_KEY == null) ? System.getProperty("APP_INSIGHTS_API_KEY") : APP_INSIGHTS_API_KEY; 
            if (APP_INSIGHTS_API_KEY == null) {
                throw new IllegalStateException("The environment variable APP_INSIGHTS_API_KEY is not configured");
            }

            APP_INSIGHTS_API_URL = (APP_INSIGHTS_API_URL == null) ? System.getProperty("APP_INSIGHTS_API_URL") : APP_INSIGHTS_API_URL; 
            if (APP_INSIGHTS_API_URL == null) {
                throw new IllegalStateException("The environment variable APP_INSIGHTS_API_URL is not configured");
            }

            // Query Application Insights
            String operationName = System.getenv(kpiId + "_OPERATION_NAME");
            String query = String.format(
                "requests | where timestamp between (todatetime('%s') .. todatetime('%s')) "
                    + "| where cloud_RoleName == '%s' "
                    //+ "| where name in ('POST /nodo-auth/node-for-psp/v1', 'POST /nodo-auth/node-for-psp/V1/') "
                    + "| where operation_Name == '%s' "
                    + "| summarize avg_duration = avg(duration) "
                    + "| extend avg_duration = iff(isnan(avg_duration), 0.0, avg_duration)"
                    + "| project avg_duration" ,
                startDate.toString(), endDate.toString(), CLOUD_ROLE_NAME, operationName
            );
            String payload = String.format("{\"query\": \"%s\"}", query);

            // Create connection
            HttpURLConnection conn = (HttpURLConnection) new URL(APP_INSIGHTS_API_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("x-api-key", APP_INSIGHTS_API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Execute request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes());
                os.flush();
            }

            // Read response
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException(String.format("executePerfKpi - %s Error during Application Insights invocation: %s", kpiId, conn.getResponseCode()));
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            conn.disconnect();
            
            // parse appinsight json response
            String avgDuration = parseAppInsightsResponse(response.toString());

            if (System.getProperty("ENVIRONMENT") == null || "TEST".equalsIgnoreCase(System.getProperty("ENVIRONMENT"))) {
                writePerfKpiData(startDate, endDate, kpiId, avgDuration, context);
            }

            context.getLogger().info(String.format("PerformanceKpiService - %s record successfully inserted into ADX, average[%s]", kpiId, avgDuration));

            return Double.valueOf(avgDuration);

        } catch (Exception e) {
            context.getLogger().severe(String.format("PerformanceKpiService - %s Error executing KPI calculation: %s", kpiId, e.getMessage()));
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Utility method that parse the AI response and return the result as string
     * @param response response to parse
     * @return
     */
    private String parseAppInsightsResponse(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
    
            JsonNode rootNode = objectMapper.readTree(response);  
            JsonNode tablesNode = rootNode.path("tables");
            if (tablesNode.isArray() && tablesNode.size() > 0) {
                JsonNode firstTable = tablesNode.get(0);
                JsonNode rowsNode = firstTable.path("rows");
    
                if (rowsNode.isArray() && rowsNode.size() > 0) {
                    JsonNode firstRow = rowsNode.get(0);
                    if (firstRow.isArray() && firstRow.size() > 0) {
                        // return avg value
                        return firstRow.get(0).asText(); 
                    }
                }
            }
            return "0";
        } catch (Exception e) {
            throw new RuntimeException("parseAppInsightsResponse - Error while parsing JSON response from AppInsight", e);
        }
    }

    /**
     * Computes the PERF-01 kpi by making a call to betterstack api (status page) in order
     * to retrieve the availability of NDP
     * @param startDate date from
     * @param endDate date to
     * @param context Azure function context
     * @throws Exception
     */
    public void executePerf01Kpi(LocalDateTime startDate, LocalDateTime endDate, ExecutionContext context) throws Exception {

        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        String fromDate = startDate.format(FORMATTER);
        String toDate = endDate.format(FORMATTER);
        
        context.getLogger().info(String.format("executePerf01Kpi - Invoking status page api startDate[%s] endDate[%s]", fromDate, toDate));
        
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
            
            context.getLogger().info(String.format("executePerf01Kpi - PERF-01 writing the kpi on ADX, availability[%s]", availabilty));

            // write kpi to db
            writePerfKpiData(startDate, endDate, "PERF-01", availabilty, context);
            
        } else {
            throw new RuntimeException(String.format("executePerf01Kpi - %s Error executing KPI calculation: %s - %s",
            "PERF-01", response.statusCode(), response.body()));
        }
    }   

    /**
     * Utility method that save the computed kpis on ADX inside the custom table
     * @param databaseName db name (es. re)
     * @param tableName table name
     * @param startDate date from
     * @param endDate date to
     * @param kpiName kpi to save
     * @param kpiValue kpi value
     * @param context Azure function context
     * @throws Exception
     */
    public void writePerfKpiData(LocalDateTime startDate,
            LocalDateTime endDate,
            String kpiName,
            String kpiValue, ExecutionContext context) throws Exception {

        // Formatting date
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String csvData = String.format("%s,%s,%s,%s,%s\n",
                now.format(formatter),
                startDate.format(formatter),
                endDate.format(formatter),
                kpiName,
                kpiValue);

        context.getLogger()
                .info(String.format("writePerfKpiData - Inserting data into [%s]: %s", ADX_PERF_TABLE, csvData));

        // Creazione della connessione
        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();

        if (csb == null || csb.getClusterUrl() == null) {
            throw new IllegalStateException("Cluster URL is null! Ensure environment variables are set correctly.");
        }

        // Ingestione dati come stream CSV
        try (ByteArrayInputStream ingestStream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        IngestClient ingestClient = IngestClientFactory.createClient(csb)) {

            IngestionProperties ingestionProperties = new IngestionProperties(ADX_DB_NAME, ADX_PERF_TABLE);
            ingestionProperties.setDataFormat(IngestionProperties.DataFormat.CSV);
            ingestionProperties.setFlushImmediately(true); // Forza il flush immediato

            StreamSourceInfo sourceInfo = new StreamSourceInfo(ingestStream);

            // Ingestione dati
            ingestClient.ingestFromStream(sourceInfo, ingestionProperties);

            context.getLogger().info(
                    String.format("writePerfKpiData - %s successfully inserted into [%s]", kpiName, ADX_PERF_TABLE));

        } catch (Exception e) {
            context.getLogger().severe(String.format("writePerfKpiData - Error inserting data into [%s]: %s",
                    ADX_PERF_TABLE, e.getMessage()));
            throw e;
        }
    }


    /**
     * Perform the query on ADX custom table in order to compute the comma separated string
     * to send to data lake
     * @param startDate date from
     * @param endDate date to
     * @param context azure function context
     * @return
     * @throws Exception
     */
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
        
        context.getLogger().info(String.format("queryKpiAverages - invoking app insigths query"));
        
        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();
        Client client = ClientFactory.createClient(csb);
        
        KustoOperationResult result = client.executeQuery(ADX_DB_NAME, query);
        KustoResultSetTable resultSet = result.getPrimaryResults();
        if (resultSet.next()) {
            String avg_PERF01 = "100.00";
            try {
                avg_PERF01 = resultSet.getString("avg_PERF01");
                double perf01d = Double.valueOf(avg_PERF01).doubleValue();
                DecimalFormat df = new DecimalFormat("#.00");
                avg_PERF01 = df.format(perf01d);
            } catch (Exception e) {
                context.getLogger().severe(String.format("queryKpiAverages - error while getting avg_PERF01 from resultset: %s", e.getMessage()));
                avg_PERF01 = "0.00";
            }

            String avg_PERF02 = "0"; 
            String avg_PERF02E = "0"; 
            String avg_PERF03 = "0"; 
            String avg_PERF04 = "0"; 
            String avg_PERF05 = "0"; 
            String avg_PERF06 = "0"; 
            
            try {
                avg_PERF02 = resultSet.getString("avg_PERF02") != null ? resultSet.getString("avg_PERF02") : "0";
                avg_PERF02E = resultSet.getString("avg_PERF02E") != null ? resultSet.getString("avg_PERF02E") : "0";
                avg_PERF03 = resultSet.getString("avg_PERF03") != null ? resultSet.getString("avg_PERF03") : "0";
                avg_PERF04 = resultSet.getString("avg_PERF04") != null ? resultSet.getString("avg_PERF04") : "0";
                avg_PERF05 = resultSet.getString("avg_PERF05") != null ? resultSet.getString("avg_PERF05") : "0";
                avg_PERF06 = resultSet.getString("avg_PERF06") != null ? resultSet.getString("avg_PERF06") : "0";
            } catch (Exception e) {
                context.getLogger().severe(String.format("queryKpiAverages - error while getting kpi from resultset: %s", e.getMessage()));
            }
            
            context.getLogger().severe(String.format("queryKpiAverages - kpi averages computed"));
            
            return String.format("%s,%s,%s,%s,%s,%s,%s",
                avg_PERF01, avg_PERF02, avg_PERF02E, avg_PERF03, avg_PERF04, avg_PERF05, avg_PERF06);
            
        } else {
            context.getLogger().severe(String.format("queryKpiAverages - the query produced no result, returning the default value"));
            return "0.0,0.0,0.0,0.0,0.0,0.0,0.0"; // Default values
        }
    }

    /**
     * Send kpi message to evh
     * @param message message to send
     * @param context Azure function context
     * @throws Exception
     */
    public void sendToEventHub(String message, ExecutionContext context) throws Exception {

        context.getLogger().severe(String.format("sendToEventHub - sending data to evh: %s", message));

        try {

            if (EVENT_HUB_NAMESPACE == null || EVENT_HUB_NAME == null || EVENT_HUB_KEY_NAME == null || EVENT_HUB_KEY == null) {
                throw new IllegalArgumentException(" sendToEventHub - Environment variables EVENT_HUB_NAMESPACE, EVENT_HUB_NAME, EVENT_HUB_KEY_NAME, or EVENT_HUB_KEY are not set.");
            }

            // Creating producer client
            AzureNamedKeyCredential credential = new AzureNamedKeyCredential(EVENT_HUB_KEY_NAME, EVENT_HUB_KEY);
            EventHubProducerClient producer = new EventHubClientBuilder()
                .credential(EVENT_HUB_NAMESPACE, EVENT_HUB_NAME, credential)
                .buildProducerClient();

            // Create a batch to send the message
            EventDataBatch batch = producer.createBatch();
            EventData eventData = new EventData(message);

            if (!batch.tryAdd(eventData)) {
                throw new IllegalStateException("sendToEventHub - Event data is too large to fit in the batch.");
            }

            // Send data to evh
            producer.send(batch);
            context.getLogger().info("sendToEventHub - Data successfully sent to Event Hub");

        } catch (Exception e) {
            context.getLogger().severe(String.format("sendToEventHub - Error while sending data to Event Hub: %s", e.getMessage()));
            throw e;
        }

    }
}
