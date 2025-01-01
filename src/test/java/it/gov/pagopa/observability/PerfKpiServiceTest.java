package it.gov.pagopa.observability;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import com.microsoft.azure.functions.ExecutionContext;

import it.gov.pagopa.observability.service.PerfKpiService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PerfKpiServiceTest {

    private PerfKpiService service;

    @BeforeEach
    void setUp() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        service = PerfKpiService.getInstance(context);
    }

    @Test
    void testExecutePerf02Kpi() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.of(2024, 12, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2024, 12, 31, 23, 59);

        // Act & Assert
        assertDoesNotThrow(() -> service.executePerf02Kpi(startDate, endDate));
    }

    @Test
    void testExecutePerfKpi() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.of(2024, 12, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2024, 12, 31, 23, 59);

        // Act & Assert
        assertDoesNotThrow(() -> service.executePerfKpi(startDate, endDate, "PERF-03"));
    }

    @Test
    void testExecutePerfKpiWithNoData() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.of(2024, 12, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2024, 12, 31, 23, 59);

        // Act & Assert
        assertDoesNotThrow(() -> service.executePerfKpi(startDate, endDate, "PERF-NOT-EXISTENT"));
    }
}
