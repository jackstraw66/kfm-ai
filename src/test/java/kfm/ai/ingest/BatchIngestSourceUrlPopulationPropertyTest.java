package kfm.ai.ingest;

import kfm.ai.types.SetList;
import kfm.ai.types.SongSet;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for source URL population in the batch ingest pipeline.
 *
 * // Feature: setlist-batch-ingest, Property 3: Source URL Population
 *
 * <p>Validates: Requirements 2.3, 7.2</p>
 *
 * <p>For any successfully parsed SetList and any non-empty URL string, the SetList entity
 * SHALL have its {@code sourceUrl} field set to exactly the URL from the API response.</p>
 */
class BatchIngestSourceUrlPopulationPropertyTest {

    /**
     * Property 3: The SetList passed to persistShow() retains the sourceUrl
     * set by the API client mapping.
     */
    @Property(tries = 200)
    void sourceUrlIsSetToExactlyTheFetchedUrl(
            @ForAll("setlistFmUrls") String url
    ) {
        // Build a SetList with sourceUrl already set (as the API client does)
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1977, 5, 8, 20, 0))
                .sourceUrl(url)
                .songSets(new ArrayList<>())
                .build();

        SetlistFmApiClient apiClient = mock(SetlistFmApiClient.class);
        ShowPersistenceHelper persistenceHelper = mock(ShowPersistenceHelper.class);

        when(apiClient.fetchAllSetlists()).thenReturn(List.of(setList));

        ArgumentCaptor<SetList> captor = ArgumentCaptor.forClass(SetList.class);
        when(persistenceHelper.persistShow(captor.capture())).thenReturn(PersistResult.INGESTED);

        BatchIngestService service = new BatchIngestService(apiClient, persistenceHelper);
        service.runFullIngest();

        SetList captured = captor.getValue();
        assertEquals(url, captured.getSourceUrl(),
                "sourceUrl must be preserved from API mapping. Expected: " + url);
    }

    @Provide
    Arbitrary<String> setlistFmUrls() {
        return Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(5).ofMaxLength(30)
                .map(slug -> "https://www.setlist.fm/setlist/" + slug);
    }
}
