package kfm.ai.ingest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import kfm.ai.types.SetList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full batch ingestion pipeline: fetches all Grateful Dead
 * setlists from the setlist.fm REST API, deduplicates against the database,
 * and persists new shows.
 *
 * <p>Only one ingest run may execute at a time; concurrent invocations are
 * rejected immediately with {@link IngestAlreadyRunningException}.</p>
 *
 * <p>This method does NOT run in a transaction itself. Each show is persisted
 * in its own transaction via {@link ShowPersistenceHelper} so that failures
 * on one show do not roll back others.</p>
 */
@Slf4j
@Service
public class BatchIngestService {

    private final SetlistFmApiClient setlistFmApiClient;
    private final ShowPersistenceHelper showPersistenceHelper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public BatchIngestService(SetlistFmApiClient setlistFmApiClient,
                              ShowPersistenceHelper showPersistenceHelper) {
        this.setlistFmApiClient = setlistFmApiClient;
        this.showPersistenceHelper = showPersistenceHelper;
    }

    /**
     * Returns true if an ingest run is currently in progress.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Requests cancellation of the current ingest run.
     * The run will stop after completing the current show being processed.
     *
     * @return true if a run was in progress and cancellation was requested,
     *         false if no run is in progress
     */
    public boolean cancel() {
        if (running.get()) {
            cancelRequested.set(true);
            log.info("Cancellation requested for current ingest run");
            return true;
        }
        return false;
    }

    /**
     * Executes the full ingest pipeline synchronously.
     *
     * @return IngestSummary with discovered/ingested/skipped/failed counts
     * @throws IngestAlreadyRunningException if another run is already in progress
     */
    public IngestSummary runFullIngest() {
        if (!running.compareAndSet(false, true)) {
            throw new IngestAlreadyRunningException();
        }

        try {
            log.info("Starting batch ingest from setlist.fm API");
            List<SetList> setlists = setlistFmApiClient.fetchAllSetlists();

            int discovered = setlists.size();
            int ingested = 0;
            int skipped = 0;
            int failed = 0;

            if (discovered == 0) {
                log.error("No setlists fetched from API");
                return new IngestSummary(0, 0, 0, 0);
            }

            log.info("Fetched {} setlists from API, beginning persistence", discovered);

            for (int i = 0; i < setlists.size(); i++) {
                // Check for cancellation
                if (cancelRequested.get()) {
                    log.info("Ingest cancelled after processing {}/{} setlists", i, discovered);
                    break;
                }

                SetList setList = setlists.get(i);

                // Persist in its own transaction via the helper
                PersistResult result = showPersistenceHelper.persistShow(setList);
                switch (result) {
                    case INGESTED -> ingested++;
                    case SKIPPED -> skipped++;
                    case FAILED -> failed++;
                }

                if ((i + 1) % 100 == 0) {
                    log.info("Persistence progress: {}/{} processed (ingested={}, skipped={}, failed={})",
                            i + 1, discovered, ingested, skipped, failed);
                }
            }

            log.info("Ingest complete: discovered={}, ingested={}, skipped={}, failed={}",
                    discovered, ingested, skipped, failed);

            return new IngestSummary(discovered, ingested, skipped, failed);
        } finally {
            cancelRequested.set(false);
            running.set(false);
        }
    }
}
