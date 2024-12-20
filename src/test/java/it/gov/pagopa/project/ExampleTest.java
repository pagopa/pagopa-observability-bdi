package it.gov.pagopa.project;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExampleTest {

    @Spy
    Example function;

    @Mock
    ExecutionContext context;

    @Test
    void runOk() {
        // test precondition
        Logger logger = Logger.getLogger("example-test-logger");
        when(context.getLogger()).thenReturn(logger);

        final HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));
        doReturn(builder).when(builder).header(anyString(), anyString());
        doReturn(builder).when(builder).body(anyString());

        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);
        doReturn(HttpStatus.OK).when(responseMock).getStatus();
        doReturn(responseMock).when(builder).build();

        // test execution
        HttpResponseMessage response = function.run(request, context);

        // test assertion
        assertEquals(HttpStatus.OK, response.getStatus());
    }
}
