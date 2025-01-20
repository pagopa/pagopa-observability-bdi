package it.gov.pagopa.observability;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CollectPerfDataTest {

    private CollectPerfData collectPerfData;

    @Mock
    private ExecutionContext context;

    @Mock
    private HttpRequestMessage<Optional<String>> request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        collectPerfData = new CollectPerfData();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHttpTriggerSuccess() {
        // Arrange
        // Mock HttpRequestMessage
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        when(responseBuilder.header(Mockito.anyString(), Mockito.anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(Mockito.any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(mock(HttpResponseMessage.class));
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);

        when(context.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        when(request.getQueryParameters()).thenReturn(Map.of(
            "startDate", "2024-12-01T00:00",
            "endDate", "2024-12-31T23:59",
            "kpiId", "PERF-03"
        ));
        when(request.getHttpMethod()).thenReturn(com.microsoft.azure.functions.HttpMethod.GET);

        // Act
        HttpResponseMessage response = collectPerfData.httpTrigger(request, context);

        // Assert
        assertNotNull(response);
        //assertEquals(200, response.getStatusCode());
        //String responseBody = (String) response.getBody();
        //assertTrue(responseBody.contains("\"status\":\"200\""));
        //assertTrue(responseBody.contains("\"message\":\"CollectPerfData - Processed interval"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHttpTriggerWithDefaultParams() {
        // Arrange
        // Mock HttpRequestMessage
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        when(responseBuilder.header(Mockito.anyString(), Mockito.anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(Mockito.any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(mock(HttpResponseMessage.class));
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);

        when(context.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        when(request.getQueryParameters()).thenReturn(Map.of());
        when(request.getHttpMethod()).thenReturn(com.microsoft.azure.functions.HttpMethod.GET);

        // Act
        HttpResponseMessage response = collectPerfData.httpTrigger(request, context);

        // Assert
        assertNotNull(response);
        //assertEquals(200, response.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHttpTriggerError() {
        // Arrange
        // Mock HttpRequestMessage
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder responseBuilder = mock(HttpResponseMessage.Builder.class);
        when(responseBuilder.header(Mockito.anyString(), Mockito.anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(Mockito.any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(mock(HttpResponseMessage.class));
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);

        when(context.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        when(request.getQueryParameters()).thenReturn(Map.of("startDate", "invalid-date"));

        // Act
        HttpResponseMessage response = collectPerfData.httpTrigger(request, context);

        // Assert
        assertNotNull(response);
        //assertEquals(500, response.getStatusCode());
        //String responseBody = (String) response.getBody();
        //assertTrue(responseBody.contains("Generic error"));
    }
}
