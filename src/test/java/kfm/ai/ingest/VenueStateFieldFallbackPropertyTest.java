package kfm.ai.ingest;

import kfm.ai.ingest.SetlistFmApiClient.ApiArtist;
import kfm.ai.ingest.SetlistFmApiClient.ApiCity;
import kfm.ai.ingest.SetlistFmApiClient.ApiSetlist;
import kfm.ai.ingest.SetlistFmApiClient.ApiVenue;
import kfm.ai.types.SetList;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Property-based test for state field fallback logic in venue mapping.
 *
 * // Feature: venue-data-capture, Property 4: State Field Fallback Logic
 *
 * <p><b>Validates: Requirements 4.3, 4.6</b></p>
 *
 * <p>For any API response containing venue city data, the mapToEntity() method SHALL set
 * the state field to stateCode when stateCode is non-blank, otherwise to state when state
 * is non-blank, otherwise to null. Whitespace-only values are treated as blank.</p>
 */
class VenueStateFieldFallbackPropertyTest {

    private static final String VALID_EVENT_DATE = "08-05-1977";

    private final SetlistFmApiClient client = new SetlistFmApiClient(
            org.springframework.web.client.RestClient.builder(),
            testProperties()
    );

    /**
     * When both stateCode and state are non-blank, stateCode is preferred.
     */
    @Property(tries = 100)
    void stateCodePreferredWhenBothPresent(
            @ForAll("nonBlankStrings") String stateCode,
            @ForAll("nonBlankStrings") String stateName
    ) {
        ApiCity city = new ApiCity("TestCity", stateName, stateCode);
        SetList result = mapWithCity(city);

        assertEquals(stateCode, result.getState(),
                "stateCode should be preferred when both stateCode and state are non-blank");
    }

    /**
     * When only stateCode is non-blank (state is null or blank), stateCode is used.
     */
    @Property(tries = 100)
    void stateCodeUsedWhenStateAbsent(
            @ForAll("nonBlankStrings") String stateCode,
            @ForAll("nullOrBlankStrings") String stateName
    ) {
        ApiCity city = new ApiCity("TestCity", stateName, stateCode);
        SetList result = mapWithCity(city);

        assertEquals(stateCode, result.getState(),
                "stateCode should be used when state is null or blank");
    }

    /**
     * When stateCode is null or blank but state is non-blank, state name is used as fallback.
     */
    @Property(tries = 100)
    void stateNameUsedAsFallbackWhenStateCodeAbsent(
            @ForAll("nullOrBlankStrings") String stateCode,
            @ForAll("nonBlankStrings") String stateName
    ) {
        ApiCity city = new ApiCity("TestCity", stateName, stateCode);
        SetList result = mapWithCity(city);

        assertEquals(stateName, result.getState(),
                "state name should be used as fallback when stateCode is null or blank");
    }

    /**
     * When both stateCode and state are null or blank, state field is null.
     */
    @Property(tries = 100)
    void stateIsNullWhenBothAbsent(
            @ForAll("nullOrBlankStrings") String stateCode,
            @ForAll("nullOrBlankStrings") String stateName
    ) {
        ApiCity city = new ApiCity("TestCity", stateName, stateCode);
        SetList result = mapWithCity(city);

        assertNull(result.getState(),
                "state should be null when both stateCode and state are null or blank");
    }

    /**
     * Whitespace-only stateCode triggers fallback to state name.
     */
    @Property(tries = 100)
    void whitespaceOnlyStateCodeFallsBackToState(
            @ForAll("whitespaceOnlyStrings") String stateCode,
            @ForAll("nonBlankStrings") String stateName
    ) {
        ApiCity city = new ApiCity("TestCity", stateName, stateCode);
        SetList result = mapWithCity(city);

        assertEquals(stateName, result.getState(),
                "whitespace-only stateCode should trigger fallback to state name");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private SetList mapWithCity(ApiCity city) {
        ApiVenue venue = new ApiVenue("v1", "Test Venue", city);
        ApiSetlist apiSetlist = new ApiSetlist(
                "id1", "v1", VALID_EVENT_DATE, "https://www.setlist.fm/setlist/id1",
                null, new ApiArtist("mbid", "Test Artist"), venue, null
        );
        return client.mapToEntity(apiSetlist);
    }

    private static BatchIngestProperties testProperties() {
        BatchIngestProperties props = new BatchIngestProperties();
        props.setApiKey("test-key");
        props.setRequestDelayMs(100);
        return props;
    }

    // ── Providers ────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> nullOrBlankStrings() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just(" \t\n")
        );
    }

    @Provide
    Arbitrary<String> whitespaceOnlyStrings() {
        return Arbitraries.oneOf(
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just(" \t\n"),
                Arbitraries.just("  \n  ")
        );
    }
}
