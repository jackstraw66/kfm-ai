package kfm.ai.ingest;

import kfm.ai.ingest.SetlistFmApiClient.ApiSet;
import kfm.ai.ingest.SetlistFmApiClient.ApiSetlist;
import kfm.ai.ingest.SetlistFmApiClient.ApiSets;
import kfm.ai.ingest.SetlistFmApiClient.ApiSong;
import kfm.ai.ingest.SetlistFmApiClient.SetlistsResponse;
import kfm.ai.types.SetList;
import kfm.ai.types.SongSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.ResponseSpec;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SetlistFmApiClient} mapping and error-handling logic.
 *
 * <p>Uses reflection to test the private {@code mapToEntity} and {@code mapSongSet}
 * methods in isolation, since the public {@code fetchAllSetlists} method requires
 * a live HTTP connection.</p>
 */
@ExtendWith(MockitoExtension.class)
class SetlistFmApiClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private ResponseSpec responseSpec;

    private SetlistFmApiClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        BatchIngestProperties properties = new BatchIngestProperties();
        properties.setApiKey("test-key");
        properties.setRequestDelayMs(100);

        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), any(String[].class))).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        client = new SetlistFmApiClient(restClientBuilder, properties);
    }

    // ── mapToEntity tests via reflection ────────────────────────────────

    @Test
    void mapToEntity_nullEventDate_returnsNull() throws Exception {
        ApiSetlist apiSetlist = new ApiSetlist("id1", "v1", null, "http://url", null, null, null, null);

        SetList result = invokeMapToEntity(apiSetlist);

        assertNull(result);
    }

    @Test
    void mapToEntity_blankEventDate_returnsNull() throws Exception {
        ApiSetlist apiSetlist = new ApiSetlist("id1", "v1", "  ", "http://url", null, null, null, null);

        SetList result = invokeMapToEntity(apiSetlist);

        assertNull(result);
    }

    @Test
    void mapToEntity_validDate_parsedCorrectly() throws Exception {
        ApiSetlist apiSetlist = new ApiSetlist("id1", "v1", "08-05-1977", "http://example.com/setlist", null, null, null, null);

        SetList result = invokeMapToEntity(apiSetlist);

        assertNotNull(result);
        assertEquals(1977, result.getDate().getYear());
        assertEquals(5, result.getDate().getMonthValue());
        assertEquals(8, result.getDate().getDayOfMonth());
        assertEquals("http://example.com/setlist", result.getSourceUrl());
    }

    @Test
    void mapToEntity_nullUrl_generatesFallbackUrl() throws Exception {
        ApiSetlist apiSetlist = new ApiSetlist("abc123", "v1", "10-06-1973", null, null, null, null, null);

        SetList result = invokeMapToEntity(apiSetlist);

        assertNotNull(result);
        assertEquals("https://www.setlist.fm/setlist/abc123", result.getSourceUrl());
    }

    @Test
    void mapToEntity_unparseableDate_returnsNull() throws Exception {
        ApiSetlist apiSetlist = new ApiSetlist("id1", "v1", "not-a-date", "http://url", null, null, null, null);

        SetList result = invokeMapToEntity(apiSetlist);

        assertNull(result);
    }

    @Test
    void mapToEntity_withSets_mapsSongSets() throws Exception {
        ApiSong song1 = new ApiSong("Truckin'", null, false);
        ApiSong song2 = new ApiSong("Dark Star", "(with Phil)", false);
        ApiSet set1 = new ApiSet("Set 1", null, List.of(song1, song2));
        ApiSet encore = new ApiSet("Encore", 1, List.of());
        ApiSets sets = new ApiSets(List.of(set1, encore));

        ApiSetlist apiSetlist = new ApiSetlist("id1", "v1", "01-01-1970",
                "http://url", null, null, null, sets);

        SetList result = invokeMapToEntity(apiSetlist);

        assertNotNull(result);
        assertEquals(2, result.getSongSets().size());

        SongSet firstSet = result.getSongSets().get(0);
        assertEquals(1, firstSet.getOrdinal());
        assertFalse(firstSet.isEncore());
        assertEquals(2, firstSet.getSongs().size());
        assertEquals("Truckin'", firstSet.getSongs().get(0).getTitle());

        SongSet encoreSet = result.getSongSets().get(1);
        assertEquals(2, encoreSet.getOrdinal());
        assertTrue(encoreSet.isEncore());
    }

    @Test
    void mapSongSet_segueDetectedAndCleanedFromInfo() throws Exception {
        ApiSong song = new ApiSong("China Cat Sunflower", "(>)", false);
        ApiSet apiSet = new ApiSet("Set 1", null, List.of(song));

        SongSet result = invokeMapSongSet(apiSet, 1);

        assertTrue(result.getSongs().get(0).isSegue());
        // Info should be null after cleaning the segue marker
        assertNull(result.getSongs().get(0).getAnnotation());
    }

    @Test
    void mapSongSet_infoPreservedWhenNoSegue() throws Exception {
        ApiSong song = new ApiSong("Scarlet Begonias", "First time since 2019", false);
        ApiSet apiSet = new ApiSet("Set 1", null, List.of(song));

        SongSet result = invokeMapSongSet(apiSet, 1);

        assertFalse(result.getSongs().get(0).isSegue());
        assertEquals("First time since 2019", result.getSongs().get(0).getAnnotation());
    }

    @Test
    void mapSongSet_nullSongName_defaultsToUnknown() throws Exception {
        ApiSong song = new ApiSong(null, null, false);
        ApiSet apiSet = new ApiSet("Set 1", null, List.of(song));

        SongSet result = invokeMapSongSet(apiSet, 1);

        assertEquals("Unknown", result.getSongs().get(0).getTitle());
    }

    @Test
    void mapSongSet_nullSongList_producesEmptySongs() throws Exception {
        ApiSet apiSet = new ApiSet("Set 1", null, null);

        SongSet result = invokeMapSongSet(apiSet, 1);

        assertTrue(result.getSongs().isEmpty());
    }

    // ── fetchAllSetlists error handling ──────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void fetchAllSetlists_restClientException_returnsEmptyList() {
        RequestHeadersUriSpec rawSpec = mock(RequestHeadersUriSpec.class);
        when(restClient.get()).thenReturn(rawSpec);
        when(rawSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(SetlistsResponse.class))
                .thenThrow(new RestClientException("Connection refused"));

        List<SetList> result = client.fetchAllSetlists();

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchAllSetlists_nullResponse_returnsEmptyList() {
        RequestHeadersUriSpec rawSpec = mock(RequestHeadersUriSpec.class);
        when(restClient.get()).thenReturn(rawSpec);
        when(rawSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(SetlistsResponse.class)).thenReturn(null);

        List<SetList> result = client.fetchAllSetlists();

        assertTrue(result.isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private SetList invokeMapToEntity(ApiSetlist apiSetlist) throws Exception {
        Method method = SetlistFmApiClient.class.getDeclaredMethod("mapToEntity", ApiSetlist.class);
        method.setAccessible(true);
        return (SetList) method.invoke(client, apiSetlist);
    }

    private SongSet invokeMapSongSet(ApiSet apiSet, int ordinal) throws Exception {
        Method method = SetlistFmApiClient.class.getDeclaredMethod("mapSongSet", ApiSet.class, int.class);
        method.setAccessible(true);
        return (SongSet) method.invoke(client, apiSet, ordinal);
    }
}
