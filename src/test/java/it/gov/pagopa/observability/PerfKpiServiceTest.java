package it.gov.pagopa.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientFactory;
import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.KustoResultSetTable;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.ingest.IngestClient;
import com.microsoft.azure.kusto.ingest.QueuedIngestClient;

import it.gov.pagopa.observability.helper.PerfKpiHelper;
import it.gov.pagopa.observability.service.PerfKpiService;

@ExtendWith(MockitoExtension.class)
public class PerfKpiServiceTest {

    @Mock
    private ExecutionContext context;

    @Mock
    private Client kustoClient;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private KustoOperationResult kustoOperationResult;

    @Mock
    private KustoResultSetTable kustoResultSetTable;

    @Mock
    private IngestClient ingestClient;

    @Mock
    private ConnectionStringBuilder connectionStringBuilder;

    @Mock
    private QueuedIngestClient queuedIngestClient;

    @InjectMocks
    private PerfKpiService perfKpiService;

    private MockedStatic<PerfKpiHelper> perfKpiHelperMock;
    private MockedStatic<ClientFactory> clientFactoryMock;
 
    @SuppressWarnings("unused")
    @BeforeEach
    void setUp() {
        System.setProperty("ADX_DATABASE_NAME", "default_test_db");
        System.setProperty("ADX_PERF_TABLE", "test_table");
        System.setProperty("ADX_CLUSTER_URL", "https://mock-cluster.kusto.windows.net");
        System.setProperty("ENVIRONMENT", "TEST");
        System.setProperty("APP_INSIGHTS_API_KEY", "mock-api-key");
        System.setProperty("APP_INSIGHTS_API_URL", "https://api.applicationinsights.io/v1/apps/76537955-6128-45d4-bf32-b0034ed17e4d/query");

        MockitoAnnotations.openMocks(this);
        perfKpiHelperMock = Mockito.mockStatic(PerfKpiHelper.class);
        clientFactoryMock = Mockito.mockStatic(ClientFactory.class);

        lenient().when(context.getLogger()).thenReturn(mock(java.util.logging.Logger.class));

        
        try {
            // Mock PerfKpiHelper per ConnectionStringBuilder
            ConnectionStringBuilder mockConnectionStringBuilder = mock(ConnectionStringBuilder.class);
            lenient().when(PerfKpiHelper.getConnectionStringBuilder()).thenReturn(mockConnectionStringBuilder);
    
            // Mock ClientFactory per evitare errori di connessione
            lenient().when(ClientFactory.createClient(any(ConnectionStringBuilder.class))).thenReturn(kustoClient);

            // Mock HttpURLConnection to get http 200 response code
            HttpURLConnection mockConnection = mock(HttpURLConnection.class);
            lenient().when(mockConnection.getResponseCode()).thenReturn(200);
            lenient().when(mockConnection.getInputStream()).thenReturn(
                new ByteArrayInputStream("{ \"tables\": [{ \"rows\": [[\"123.45\"]] }] }".getBytes())
            );

            // Mock OutputStream to avoid null
            OutputStream mockOutputStream = new ByteArrayOutputStream();
            lenient().when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);

            // Mock di `new URL(...)` mock
            MockedConstruction<URL> mockedUrlConstruction = mockConstruction(URL.class, (mock, context) -> {
                lenient().when(mock.openConnection()).thenReturn(mockConnection);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    @AfterEach
    void tearDown() {
        System.clearProperty("ADX_DATABASE_NAME");
    
        perfKpiHelperMock.close();
        clientFactoryMock.close();
    }
    

    @Test
    void testExecutePerf02Kpi() throws Exception {
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now();
    
        lenient().when(kustoClient.executeQuery(anyString(), anyString())).thenReturn(kustoOperationResult);
        when(kustoOperationResult.hasNext()).thenReturn(true);
        when(kustoOperationResult.getPrimaryResults()).thenReturn(kustoResultSetTable);
        when(kustoResultSetTable.next()).thenReturn(true);
        when(kustoResultSetTable.getInt("count")).thenReturn(42);
    
        doAnswer(invocation -> {
            String dbName = invocation.getArgument(0);
            String query = invocation.getArgument(1);
            System.out.println("execute() chiamato con: " + dbName + ", " + query);
            return kustoOperationResult;
        }).when(kustoClient).executeQuery(anyString(), anyString());
    
        String result = perfKpiService.executePerf02Kpi(startDate, endDate, context);
    
        assertEquals("42", result);
    }
    


    @Test
    void testExecutePerfKpi() throws Exception {
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now();
        String kpiId = "PERF-03";

        String result = perfKpiService.executePerfKpi(startDate, endDate, kpiId, context);

        assertEquals(result, 123.450000d);
    }
}
