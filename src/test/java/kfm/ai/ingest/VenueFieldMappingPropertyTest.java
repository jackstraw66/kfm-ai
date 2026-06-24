package kfm.ai.ingest;

import kfm.ai.ingest.SetlistFmApiClient.ApiCity;
import kfm.ai.ingest.SetlistFmApiClient.ApiSetlist;
import kfm.ai.ingest.SetlistFmApiClient.ApiVenue;
import kfm.ai.types.SetList;
import net.jqwik.api.*;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: venue-data-capture, Property 3: Venue Field Mapping Correctness
/**
 * Property-based test for venue field mapping correctness in the ingest pipeline.
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 4.4, 4.5, 4.6</b></p>
 *
 * <p>For any API response containing a venue with a non-blank name and a city object
 * with a non-blank city name, the mapToEntity() method SHALL produce a SetList entity
 * where venueName equals the venue name (truncated to 512 characters if longer) and
 * city equals the city name. When the venue name or city name is null, blank, or
 * whitespace-only, the corresponding entity field SHALL be null.</p>
 */
class VenueFieldMappingPropertyTest {

    private static final DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final SetlistFmApiClient client;

    VenueFieldMappingPropertyTest() {
        BatchIngestProperties properties = mock(BatchIngestProperties.class);
        when(properties.getApiKey()).thenReturn("test-key");
        RestClient.Builder builder = RestClient.builder();
        this.client = new SetlistFmApiClient(builder, properties);
    }

    /**
     * When venue has a non-blank name, venueName is set to that name (truncated to 512 chars).
     */
    @Property(tries = 100)
    void nonBlankVenueNameIsMappedWithTruncation(
            @ForAll("nonBlankStrings") String venueName
    ) {
        ApiVenue venue = new ApiVenue("v1", venueName, null);
        ApiSetlist apiSetlist = buildApiSetlist(venue);

        SetList result = client.mapToEntity(apiSetlist);

        assertNotNull(result);
        String expected = venueName.length() > 512 ? venueName.substring(0, 512) : venueName;
        assertEquals(expected, result.getVenueName());
    }

    /**
     * When venue name is null, blank, or whitespace-only, venueName is null.
     */
    @Property(tries = 100)
    void blankOrNullVenueNameResultsInNullVenueName(
            @ForAll("blankOrNullStrings") String venueName
    ) {
        ApiVenue venue = new ApiVenue("v1", venueName, null);
        ApiSetlist apiSetlist = buildApiSetlist(venue);

        SetList result = client.mapToEntity(apiSetlist);

        assertNotNull(result);
        assertNull(result.getVenueName());
    }

    /**
     * When venue is null entirely, venueName and city are both null.
     */
    @Property(tries = 100)
    void nullVenueResultsInNullFields(@ForAll("validDates") String eventDate) {
        ApiSetlist apiSetlist = new ApiSetlist(
                "id1", "v1", eventDate, "https://www.setlist.fm/setlist/test",
                null, null, null, null
        );

        SetList result = client.mapToEntity(apiSetlist);

        assertNotNull(result);
        assertNull(result.getVenueName());
        assertNull(result.getCity());
    }

    /**
     * When city has a non-blank name, city field is mapped correctly.
     */
    @Property(tries = 100)
    void nonBlankCityNameIsMapped(
            @ForAll("nonBlankStrings") String cityName
    ) {
        ApiCity city = new ApiCity(cityName, null, null);
        ApiVenue venue = new ApiVenue("v1", "Test Venue", city);
        ApiSetlist apiSetlist = buildApiSetlist(venue);

        SetList result = client.mapToEntity(apiSetlist);

        assertNotNull(result);
        assertEquals(cityName, result.getCity());
    }

    /**
     * When city name is null, blank, or whitespace-only, city field is null.
     */
    @Property(tries = 100)
    void blankOrNullCityNameResultsInNullCity(
            @ForAll("blankOrNullStrings") String cityName
    ) {
        ApiCity city = new ApiCity(cityName, null, null);
        ApiVenue venue = new ApiVenue("v1", "Test Venue", city);
        ApiSetlist apiSetlist = buildApiSetlist(venue);

        SetList result = client.mapToEntity(apiSetlist);

        assertNotNull(result);
        assertNull(result.getCity());
    }

    /**
     * When city object is null, city field is null.
     */
    @Property(tries = 100)
    void nullCityObjectResultsInNullCity(@ForAll("validDates") String eventDate) {
        ApiVenue venue = new ApiVenue("v1", "Test Venue", null);
        ApiSetlist apiSetlist = new ApiSetlist(
                "id1", "v1", eventDate, "https://www.setlist.fm/setlist/test",
                null, null, venue, null
        );

        SetList result = client.mapToEntity(apiSetlist);

        assertNotNull(result);
        assertNull(result.getCity());
    }

    /**
     * Venue names longer than 512 characters are truncated to exactly 512.
     */
    @Property(tries = 100)
    void longVenueNameIsTruncatedTo512(
            @ForAll("longStrings") String longName
    ) {
        ApiVenue venue = new ApiVenue("v1", longName, null);
        ApiSetlist apiSetlist = buildApiSetlist(venue);

        SetList result = client.mapToEntity(apiSetlist);

        assertNotNull(result);
        assertNotNull(result.getVenueName());
        assertTrue(result.getVenueName().length() <= 512);
        assertEquals(longName.substring(0, 512), result.getVenueName());
    }

    // ── Providers ────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1)
                .ofMaxLength(600);
    }

    @Provide
    Arbitrary<String> blankOrNullStrings() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just(" "),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n"),
                Arbitraries.just(" \t \n ")
        );
    }

    @Provide
    Arbitrary<String> longStrings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(513)
                .ofMaxLength(1024);
    }

    @Provide
    Arbitrary<String> validDates() {
        return Arbitraries.integers().between(1960, 2024).flatMap(year ->
                Arbitraries.integers().between(1, 12).flatMap(month ->
                        Arbitraries.integers().between(1, 28).map(day -> {
                            LocalDate date = LocalDate.of(year, month, day);
                            return date.format(EVENT_DATE_FORMAT);
                        })
                )
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ApiSetlist buildApiSetlist(ApiVenue venue) {
        String eventDate = "08-05-1977";
        return new ApiSetlist(
                "id1", "v1", eventDate, "https://www.setlist.fm/setlist/test",
                null, null, venue, null
        );
    }
}
