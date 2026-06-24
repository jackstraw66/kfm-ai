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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BatchIngestService#cancel()} behaviour.
 */
@ExtendWith(MockitoExtension.class)
class BatchIngestServiceCancelTest {

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
    void cancel_whenNotRunning_returnsFalse() {
        assertFalse(service.cancel());
    }

    @Test
    void cancel_whenRunning_returnsTrue() throws Exception {
        CountDownLatch ingestStarted = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);

        when(apiClient.fetchAllSetlists()).thenAnswer(inv -> {
            ingestStarted.countDown();
            allowFinish.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> service.runFullIngest());

        assertTrue(ingestStarted.await(5, TimeUnit.SECONDS));
        assertTrue(service.cancel());

        allowFinish.countDown();
        future.get(5, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Test
    void cancel_stopsProcessingAfterCurrentShow() throws Exception {
        CountDownLatch firstShowProcessed = new CountDownLatch(1);
        CountDownLatch allowContinue = new CountDownLatch(1);

        List<SetList> setlists = List.of(
                buildSetList("1977-05-08T20:00"),
                buildSetList("1977-05-09T20:00"),
                buildSetList("1977-05-10T20:00")
        );

        when(apiClient.fetchAllSetlists()).thenReturn(setlists);
        when(persistenceHelper.persistShow(any(SetList.class)))
                .thenAnswer(inv -> {
                    firstShowProcessed.countDown();
                    // Wait so we can cancel before 2nd show
                    allowContinue.await(5, TimeUnit.SECONDS);
                    return PersistResult.INGESTED;
                })
                .thenReturn(PersistResult.INGESTED)
                .thenReturn(PersistResult.INGESTED);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<IngestSummary> future = executor.submit(() -> service.runFullIngest());

        assertTrue(firstShowProcessed.await(5, TimeUnit.SECONDS));
        service.cancel();
        allowContinue.countDown();

        IngestSummary summary = future.get(5, TimeUnit.SECONDS);

        // Only 1 show should have been processed (the one in progress when cancel hit)
        assertEquals(3, summary.discovered());
        assertEquals(1, summary.ingested());
        executor.shutdown();
    }

    @Test
    void isRunning_falseBeforeStart() {
        assertFalse(service.isRunning());
    }

    @Test
    void isRunning_falseAfterCompletion() {
        when(apiClient.fetchAllSetlists()).thenReturn(List.of());

        service.runFullIngest();

        assertFalse(service.isRunning());
    }

    private SetList buildSetList(String dateTime) {
        return SetList.builder()
                .date(LocalDateTime.parse(dateTime))
                .sourceUrl("http://example.com/" + dateTime)
                .songSets(new ArrayList<>())
                .build();
    }
}
