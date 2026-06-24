package kfm.ai.controller;

import kfm.ai.ingest.BatchIngestService;
import kfm.ai.ingest.IngestSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IngestController}.
 */
@ExtendWith(MockitoExtension.class)
class IngestControllerTest {

    @Mock
    private BatchIngestService batchIngestService;

    private IngestController controller;

    @BeforeEach
    void setUp() {
        controller = new IngestController(batchIngestService);
    }

    @Test
    void triggerIngest_notRunning_returns202() {
        when(batchIngestService.isRunning()).thenReturn(false);

        ResponseEntity<IngestController.IngestResponse> response = controller.triggerIngest();

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("started", response.getBody().status());
    }

    @Test
    void triggerIngest_alreadyRunning_returns409() {
        when(batchIngestService.isRunning()).thenReturn(true);

        ResponseEntity<IngestController.IngestResponse> response = controller.triggerIngest();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("conflict", response.getBody().status());
    }

    @Test
    void cancelIngest_runInProgress_returns200() {
        when(batchIngestService.cancel()).thenReturn(true);

        ResponseEntity<IngestController.IngestResponse> response = controller.cancelIngest();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("cancelled", response.getBody().status());
    }

    @Test
    void cancelIngest_noRunInProgress_returns404() {
        when(batchIngestService.cancel()).thenReturn(false);

        ResponseEntity<IngestController.IngestResponse> response = controller.cancelIngest();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("not_found", response.getBody().status());
    }
}
