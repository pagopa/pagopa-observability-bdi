package it.gov.pagopa.observability;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import it.gov.pagopa.observability.service.PerfKpiService;

@ExtendWith(MockitoExtension.class)
public class CollectPerfDataTest {

    @Mock
    private PerfKpiService perfKpiService;

    @Mock
    private ExecutionContext context;

    @Mock
    private HttpRequestMessage<Optional<String>> request;

    @InjectMocks
    private CollectPerfData collectPerfData;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(mock(Logger.class));
    }

    private void mockHttpResponse(HttpStatus status) {
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
    
        when(request.createResponseBuilder(status)).thenReturn(responseBuilder);
    }
    
    @Test
    void testHttpTriggerError() throws Exception {
    
        mockHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR);

        when(request.getQueryParameters()).thenReturn(Map.of("startDate", "invalid-date"));

        HttpResponseMessage response = collectPerfData.httpTrigger(request, context);

        assertNotNull(response);
        verify(perfKpiService, never()).executePerf01Kpi(any(), any(), any());
        verify(perfKpiService, never()).executePerf02Kpi(any(), any(), any());
        verify(perfKpiService, never()).executePerf02EKpi(any(), any(), any());
    }
}
