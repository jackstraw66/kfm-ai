package kfm.ai.ingest;

import kfm.ai.types.SetList;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for summary count consistency in the batch ingest pipeline.
 *
 * // Feature: setlist-batch-ingest, Property 6: Summary Count Consistency
 *
 * <p><b>Validates: Requirements 4.3, 5.3</b></p>
 *
 * <p>For any completed Ingest_Run, the summary's {@code ingested + skipped + failed}
 * SHALL equal the number of setlists fetched, and {@code discovered} SHALL equal
 * the total count. No count SHALL be negative.</p>
 */
class BatchIngestSummaryCountConsistencyPropertyTest {

    enum UrlOutcome { SUCCESS, PERSIST_FAIL, SKIPPED }

    @Property(tries = 200)
    void summaryCountsAreConsistent(
            @ForAll("urlOutcomeBatches") List<UrlOutcome> outcomes
    ) {
        int n = outcomes.size();

        List<SetList> setlists = IntStream.range(0, n)
                .mapToObj(i -> SetList.builder()
                        .date(LocalDateTime.of(1970 + i, 1, 1, 20, 0))
                        .sourceUrl("https://www.setlist.fm/setlist/show-" + i)
                        .songSets(new ArrayList<>())
                        .build())
                .toList();

        SetlistFmApiClient apiClient = mock(SetlistFmApiClient.class);
        ShowPersistenceHelper persistenceHelper = mock(ShowPersistenceHelper.class);

        when(apiClient.fetchAllSetlists()).thenReturn(setlists);

        // Configure persistence results
        PersistResult[] results = new PersistResult[n];
        for (int i = 0; i < n; i++) {
            results[i] = switch (outcomes.get(i)) {
                case SUCCESS -> PersistResult.INGESTED;
                case PERSIST_FAIL -> PersistResult.FAILED;
                case SKIPPED -> PersistResult.SKIPPED;
            };
        }
        var stubbing = when(persistenceHelper.persistShow(any(SetList.class)));
        for (PersistResult result : results) {
            stubbing = stubbing.thenReturn(result);
        }

        BatchIngestService service = new BatchIngestService(apiClient, persistenceHelper);
        IngestSummary summary = service.runFullIngest();

        assertEquals(n, summary.discovered());
        assertEquals(n, summary.ingested() + summary.skipped() + summary.failed());
        assertTrue(summary.discovered() >= 0);
        assertTrue(summary.ingested() >= 0);
        assertTrue(summary.skipped() >= 0);
        assertTrue(summary.failed() >= 0);
    }

    @Provide
    Arbitrary<List<UrlOutcome>> urlOutcomeBatches() {
        return Arbitraries.of(UrlOutcome.class).list().ofMinSize(1).ofMaxSize(20);
    }
}
