package it.gov.pagopa.observability.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

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

    private static PerfKpiService instance;

    private String databaseName;
    private String sourceTable;
    private String destinationTable;
    private ExecutionContext context;
    
    
    private PerfKpiService(ExecutionContext context) {
        
        this.databaseName = System.getenv("DatabaseName");
        this.sourceTable = System.getenv("SourceTable");
        this.destinationTable = System.getenv("PerfTable");
        this.context = context;

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

        // insert the result into the destination table
        String data = String.format(
            ".ingest inline into table %s <| %s,%s,%s,'%s',%s",
            destinationTable,
            LocalDateTime.now(),
            startDate,
            endDate,
            "PERF-02E",
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
        context.getLogger().info(String.format("PerformanceKpiService - PERF-02E Data successfully inserted into[%s]", destinationTable));    
    }
}
