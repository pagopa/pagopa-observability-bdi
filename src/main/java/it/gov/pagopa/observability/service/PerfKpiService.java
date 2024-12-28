package it.gov.pagopa.observability.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.BearerTokenAuthenticationPolicy;
import com.azure.identity.DefaultAzureCredentialBuilder;
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

import com.azure.core.http.HttpMethod;
import it.gov.pagopa.observability.helper.PerfKpiHelper;

public class PerfKpiService {

    private static PerfKpiService instance;

    private String databaseName;
    private String sourceTable;
    private String destinationTable;
    private ExecutionContext context;
    private String APP_INSIGHTS_API_URL;
    
    private PerfKpiService(ExecutionContext context) {
        
        this.databaseName = System.getenv("ADX_DATABASE_NAME");
        this.sourceTable = System.getenv("ADX_SOURCE_TABLE");
        this.destinationTable = System.getenv("ADX_PERF_TABLE");
        this.context = context;
        this.APP_INSIGHTS_API_URL = System.getenv("AAI_API_URL");

        context.getLogger().info(String.format("|GetPerformanceKpiService|Database Name[%s] Source table[%s] Destination table[%s]", 
            databaseName, sourceTable, destinationTable));
    }

    public static synchronized PerfKpiService getInstance(ExecutionContext context) {
        if (instance == null) {
            instance = new PerfKpiService(context);
        }
        return instance;
    } 

    public void executePerf02Kpi(LocalDateTime startDate, LocalDateTime endDate) throws Exception {

        String perf02Query = String.format(
            "let start = datetime(%s); " +
            "let end = datetime(%s); " +
            "%s | where insertedTimestamp between (start .. end) " +
            "| where sottoTipoEvento == 'REQ' " +
            "| where categoriaEvento == 'INTERFACCIA' " +
            "| summarize count=count()" + 
            "| project count",
            startDate, endDate, sourceTable
        );
        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();
        Client kustoClient = ClientFactory.createClient(csb);
        KustoOperationResult result = kustoClient.execute(databaseName, perf02Query);
        int count = 0;
        KustoResultSetTable resultSet = null;
        if (result.hasNext())
            resultSet = result.getPrimaryResults();
            if (resultSet.next()) {
                count = resultSet.getInt("count");
        }
        context.getLogger().info(String.format("PerformanceKpiService - PERF-02 Query Result[%s] startDate[%s] endDate[%s]", count, startDate, endDate));

        // insert the result into the destination table
        String data = String.format(
            ".ingest inline into table %s <| %s,%s,%s,'%s',%s",
            destinationTable,
            LocalDateTime.now(),
            startDate,
            endDate,
            "PERF-02",
            count
        );

        // prepare ingestion
        ByteArrayInputStream ingestQueryStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        IngestClient ingestClient = IngestClientFactory.createClient(PerfKpiHelper.getConnectionStringBuilder());
        IngestionProperties ingestionProperties = new IngestionProperties(databaseName, destinationTable);
        ingestionProperties.setDataFormat(IngestionProperties.DataFormat.CSV);
        StreamSourceInfo sourceInfo = new StreamSourceInfo(ingestQueryStream);

        // execute ingestion
        ingestClient.ingestFromStream(sourceInfo, ingestionProperties);
        ingestClient.close();
        context.getLogger().info(String.format("PerformanceKpiService - PERF-02 Data successfully inserted into[%s]", destinationTable));

    }

    public void executePerf02EKpi(LocalDateTime startDate, LocalDateTime endDate) throws Exception {

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
            startDate, endDate, sourceTable
        );

        ConnectionStringBuilder csb = PerfKpiHelper.getConnectionStringBuilder();
        Client kustoClient = ClientFactory.createClient(csb);
        KustoOperationResult result = kustoClient.execute(databaseName, perf0E2Query);
        int count = 0;
        KustoResultSetTable resultSet = null;
        if (result.hasNext())
            resultSet = result.getPrimaryResults();
            if (resultSet.next()) {
                count = resultSet.getInt("count");
        }
        context.getLogger().info(String.format("PerformanceKpiService - PERF-02E Query Result[%s] startDate[%s] endDate[%s]", count, startDate, endDate));

        writePerfKpiData(databaseName, destinationTable, startDate, endDate, "PERF-02E", Double.valueOf(count));
    }

    public void executePerfKpi(LocalDateTime startDate, LocalDateTime endDate, String kpiId) throws Exception {

        try {
            context.getLogger().info(String.format("PerformanceKpiService - %s calculating KPI for period: %s to %s", kpiId, startDate, endDate));

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

            Double avgDuration = executeAppInsightsQuery(query, kpiId);

            if (avgDuration != null) {

                writePerfKpiData(databaseName, destinationTable, startDate, endDate, kpiId, avgDuration);
                context.getLogger().info("KPI record successfully inserted into Azure Table.");

            } else {
                context.getLogger().warning(String.format("PerformanceKpiService - %s No data returned from Application Insights query", kpiId));
            }
        } catch (Exception e) {
            context.getLogger().severe(String.format("PerformanceKpiService - %s Error executing KPI calculation: %s", kpiId, e.getMessage()));
        }
    }

    private Double executeAppInsightsQuery(String query, String kpiId) throws Exception {

        TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        String apiUrl =APP_INSIGHTS_API_URL;
        String scope = "https://api.applicationinsights.io/.default";

        HttpPipelineBuilder pipelineBuilder = new HttpPipelineBuilder()
                .policies(new BearerTokenAuthenticationPolicy(credential, scope));
        var httpClient = pipelineBuilder.build();
        String body = String.format("{\"query\": \"%s\"}", query);

        HttpRequest request = new HttpRequest(HttpMethod.POST, apiUrl)
            .setHeader("Content-Type", "application/json")
            .setBody(body);

        HttpResponse response = httpClient.send(request).block();
        Double avg = null;
        if (response != null) {
            String responseBody = response.getBodyAsString().block();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode avgNode = rootNode.path("tables").get(0).path("rows").get(0);
            if (!avgNode.isMissingNode()) {
                avg = avgNode.asDouble();

            } else {
                context.getLogger().warning(String.format("PerformanceKpiService - $s No result received from Application Insights query", kpiId));
                avg = 0d;
            }
        } else {
            context.getLogger().warning(String.format("PerformanceKpiService - %s No response received from Application Insights query", kpiId));
            avg = 0d;
        }
        return avg;
    }

    private void writePerfKpiData(String databaseName, String tableName, LocalDateTime startDate, LocalDateTime endDate, String kpiName, Double kpiValue) throws Exception{

        String data = String.format(
            ".ingest inline into table %s <| %s,%s,%s,'%s',%s",
            destinationTable,
            LocalDateTime.now(),
            startDate,
            endDate,
            kpiName,
            kpiValue
        );

        // prepare ingestion
        ByteArrayInputStream ingestQueryStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        IngestClient ingestClient = IngestClientFactory.createClient(PerfKpiHelper.getConnectionStringBuilder());
        IngestionProperties ingestionProperties = new IngestionProperties(databaseName, destinationTable);
        ingestionProperties.setDataFormat(IngestionProperties.DataFormat.CSV);
        StreamSourceInfo sourceInfo = new StreamSourceInfo(ingestQueryStream);

        // execute ingestion
        ingestClient.ingestFromStream(sourceInfo, ingestionProperties);
        ingestClient.close();
        context.getLogger().info(String.format("PerformanceKpiService - %s Data successfully inserted into[%s]", kpiName, destinationTable));
    }    
}
