package it.gov.pagopa.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class JacksonCompatibilityTest {

    @Test
    void parsesStatusPageAvailabilityAsFloatingPointNumber() throws Exception {
        JsonNode root = new ObjectMapper().readTree(
            "{\"data\":{\"attributes\":{\"availability\":99.95}}}"
        );

        assertEquals("99.95", root.path("data").path("attributes").path("availability").asText());
    }
}
