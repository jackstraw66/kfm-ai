package kfm.ai.controller;

import kfm.ai.ingest.BatchIngestService;
import kfm.ai.ingest.IngestAlreadyRunningException;
import kfm.ai.ingest.IngestSummary;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/ingest")
@Slf4j
public class IngestController {

    private final BatchIngestService batchIngestService;
    private final AtomicReference<CompletableFuture<IngestSummary>> currentRun = new AtomicReference<>();

    public IngestController(BatchIngestService batchIngestService) {
        this.batchIngestService = batchIngestService;
    }

    /**
     * Triggers a full batch ingest run asynchronously.
     * Returns immediately with 202 Accepted if the run started,
     * or 409 Conflict if a run is already in progress.
     */
    @PostMapping
    public ResponseEntity<IngestResponse> triggerIngest() {
        if (batchIngestService.isRunning()) {
            return ResponseEntity.status(409)
                    .body(new IngestResponse("conflict", "An ingest run is already in progress"));
        }

        CompletableFuture<IngestSummary> future = CompletableFuture.supplyAsync(() -> {
            try {
                return batchIngestService.runFullIngest();
            } catch (IngestAlreadyRunningException ex) {
                log.warn("Ingest rejected — concurrent run detected");
                return null;
            }
        });

        currentRun.set(future);

        return ResponseEntity.accepted()
                .body(new IngestResponse("started", "Batch ingest run started"));
    }

    /**
     * Cancels the current ingest run. The run will stop after completing
     * the show currently being processed.
     * Returns 200 if cancellation was requested, or 404 if no run is active.
     */
    @DeleteMapping
    public ResponseEntity<IngestResponse> cancelIngest() {
        boolean cancelled = batchIngestService.cancel();
        if (cancelled) {
            return ResponseEntity.ok(
                    new IngestResponse("cancelled", "Cancellation requested — run will stop after current show"));
        }
        return ResponseEntity.status(404)
                .body(new IngestResponse("not_found", "No ingest run is currently in progress"));
    }

    record IngestResponse(String status, String message) {}
}
