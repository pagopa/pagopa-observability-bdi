package it.gov.pagopa.observability.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.json.JSONArray;
import org.json.JSONObject;

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
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PerfKpiService {

    private static PerfKpiService instance;

    private String databaseName;
    private String sourceTable;
    private String destinationTable;
    private ExecutionContext context;
    private String APP_INSIGHTS_API_URL;
    private String APP_INSIGHTS_API_KEY;
    
    
    private PerfKpiService(ExecutionContext context) {
        
        this.databaseName = System.getenv("ADX_DATABASE_NAME");
        this.sourceTable = System.getenv("ADX_SOURCE_TABLE");
        this.destinationTable = System.getenv("ADX_PERF_TABLE");
        this.context = context;
        this.APP_INSIGHTS_API_URL = System.getenv("AAI_API_URL");
        this.APP_INSIGHTS_API_KEY = System.getenv("AAI_API_KEY");

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

            Double avgDuration = executeAppInsightsQuery(query);

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

    private Double executeAppInsightsQuery(String query) throws Exception {

        OkHttpClient client = new OkHttpClient();
        HttpUrl.Builder urlBuilder = HttpUrl.parse(APP_INSIGHTS_API_URL).newBuilder();
        urlBuilder.addQueryParameter("query", query);

        String secret = PerfKpiHelper.getKVSecret(APP_INSIGHTS_API_KEY);
        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .addHeader("x-api-key", secret)
            .get()
            .build();

        Double avgDuration = null;
        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            String responseBody = response.body().string();
            context.getLogger().info(String.format("PerformanceKpiService - PERF-03 Application Insights Response: %s", responseBody));

            // Parse response JSON
            JSONObject json = new JSONObject(responseBody);
            JSONArray tables = json.getJSONArray("tables");
            if (tables.length() > 0) {
                JSONArray rows = tables.getJSONObject(0).getJSONArray("rows");
                if (rows.length() > 0) {
                    avgDuration = rows.getJSONArray(0).getDouble(0);
                }
            }
        } else {
            context.getLogger().warning(String.format("PerformanceKpiService - PERF-03 Failed to query Application Insights: %s", response.message()));
        }

        return avgDuration;
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
