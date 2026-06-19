package kfm.ai.ingest;

import kfm.ai.types.SetList;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for failure isolation in the batch ingest pipeline.
 *
 * // Feature: setlist-batch-ingest, Property 2: Failure Isolation
 *
 * <p><b>Validates: Requirements 1.4, 2.4, 2.5, 5.1</b></p>
 *
 * <p>For any batch of N items where K items fail (due to persistence errors),
 * all N - K non-failing items SHALL still be processed to completion.</p>
 */
class BatchIngestFailureIsolationPropertyTest {

    /**
     * Property 2: Failure Isolation.
     */
    @Property(tries = 200)
    void failingItemsDoNotPreventNonFailingItemsFromBeingProcessed(
            @ForAll("batchOutcomes") List<Boolean> outcomes
    ) {
        int n = outcomes.size();
        int k = (int) outcomes.stream().filter(fail -> fail).count();

        // Build SetList objects
        List<SetList> setlists = IntStream.range(0, n)
                .mapToObj(i -> SetList.builder()
                        .date(LocalDateTime.of(1970 + i, 1, 1, 20, 0))
                        .sourceUrl("https://www.setlist.fm/setlist/show-" + i)
                        .songSets(new ArrayList<>())
                        .build())
                .toList();

        // Set up mocks
        SetlistFmApiClient apiClient = mock(SetlistFmApiClient.class);
        ShowPersistenceHelper persistenceHelper = mock(ShowPersistenceHelper.class);

        when(apiClient.fetchAllSetlists()).thenReturn(setlists);

        // Configure persistenceHelper: FAILED for failing items, INGESTED for others
        for (int i = 0; i < n; i++) {
            // We can't easily configure per-call with index, so use sequential returns
        }

        // Build sequential answers
        PersistResult[] results = new PersistResult[n];
        for (int i = 0; i < n; i++) {
            results[i] = outcomes.get(i) ? PersistResult.FAILED : PersistResult.INGESTED;
        }
        var stubbing = when(persistenceHelper.persistShow(any(SetList.class)));
        for (PersistResult result : results) {
            stubbing = stubbing.thenReturn(result);
        }

        BatchIngestService service = new BatchIngestService(apiClient, persistenceHelper);

        IngestSummary summary = service.runFullIngest();

        assertEquals(n, summary.ingested() + summary.failed(),
                "ingested + failed must equal N=" + n);
        assertEquals(k, summary.failed(),
                "failed count must equal K=" + k);
        assertEquals(n - k, summary.ingested(),
                "ingested count must equal N-K=" + (n - k));
    }

    @Provide
    Arbitrary<List<Boolean>> batchOutcomes() {
        return Arbitraries.of(true, false).list().ofMinSize(1).ofMaxSize(20);
    }
}
