package kfm.ai.ingest;

import kfm.ai.types.SetList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for batch orchestration logic.
 *
 * <p>These tests verify orchestration behavior using mocks (no database or Spring context required):
 * <ul>
 *   <li>Transaction isolation: prior shows committed when one fails</li>
 *   <li>Concurrency guard: second invocation rejected</li>
 *   <li>All-pages-fail: zero-count summary returned</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.2, 5.4, 6.4</b></p>
 */
@ExtendWith(MockitoExtension.class)
class BatchIngestOrchestrationIntegrationTest {

    @Mock
    private SetlistFmApiClient apiClient;

    @Mock
    private ShowPersistenceHelper persistenceHelper;

    private BatchIngestService service;

    @BeforeEach
    void setUp() {
        service = new BatchIngestService(apiClient, persistenceHelper);
    }

    @Test
    void transactionIsolation_priorShowsCommittedWhenOneFails() {
        // 3 setlists fetched from API
        List<SetList> setlists = List.of(
                buildSetList(LocalDateTime.of(1977, 5, 8, 20, 0), "url1"),
                buildSetList(LocalDateTime.of(1977, 5, 9, 20, 0), "url2"),
                buildSetList(LocalDateTime.of(1977, 5, 10, 20, 0), "url3")
        );
        when(apiClient.fetchAllSetlists()).thenReturn(setlists);

        // Persistence: INGESTED, FAILED, INGESTED
        when(persistenceHelper.persistShow(any(SetList.class)))
                .thenReturn(PersistResult.INGESTED)
                .thenReturn(PersistResult.FAILED)
                .thenReturn(PersistResult.INGESTED);

        IngestSummary summary = service.runFullIngest();

        assertEquals(3, summary.discovered());
        assertEquals(2, summary.ingested());
        assertEquals(1, summary.failed());
        assertEquals(0, summary.skipped());
        verify(persistenceHelper, times(3)).persistShow(any(SetList.class));
    }

    @Test
    void concurrencyGuard_secondInvocationRejected() throws Exception {
        CountDownLatch apiEnteredLatch = new CountDownLatch(1);
        CountDownLatch allowApiToFinishLatch = new CountDownLatch(1);

        when(apiClient.fetchAllSetlists()).thenAnswer(invocation -> {
            apiEnteredLatch.countDown();
            allowApiToFinishLatch.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        AtomicReference<IngestSummary> firstCallResult = new AtomicReference<>();
        AtomicReference<Throwable> firstCallError = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> firstCall = executor.submit(() -> {
            try {
                firstCallResult.set(service.runFullIngest());
            } catch (Throwable t) {
                firstCallError.set(t);
            }
        });

        assertTrue(apiEnteredLatch.await(5, TimeUnit.SECONDS));
        assertThrows(IngestAlreadyRunningException.class, () -> service.runFullIngest());

        allowApiToFinishLatch.countDown();
        firstCall.get(5, TimeUnit.SECONDS);

        assertNull(firstCallError.get());
        assertNotNull(firstCallResult.get());
        executor.shutdown();
    }

    @Test
    void allPagesFail_zeroCountSummaryReturned() {
        when(apiClient.fetchAllSetlists()).thenReturn(List.of());

        IngestSummary summary = service.runFullIngest();

        assertEquals(0, summary.discovered());
        assertEquals(0, summary.ingested());
        assertEquals(0, summary.skipped());
        assertEquals(0, summary.failed());
        verify(persistenceHelper, never()).persistShow(any(SetList.class));
    }

    private SetList buildSetList(LocalDateTime date, String url) {
        return SetList.builder()
                .date(date)
                .sourceUrl(url)
                .songSets(new ArrayList<>())
                .build();
    }
}
