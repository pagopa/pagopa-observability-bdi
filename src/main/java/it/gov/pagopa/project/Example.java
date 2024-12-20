package it.gov.pagopa.project;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import javax.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class Example {

    /**
     * This function will be invoked when a Http Trigger occurs
     */
    @FunctionName("ExampleFunction")
    public HttpResponseMessage run (
            @HttpTrigger(
                    name = "ExampleTrigger",
                    methods = {HttpMethod.GET},
                    route = "example",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        String message = String.format("it.gov.pagopa.project.Example function called at: %s", LocalDateTime.now());
        logger.log(Level.INFO, () -> message);

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", MediaType.TEXT_PLAIN)
                .body(message)
                .build();
    }
}
