package kfm.ai.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for API venue deserialization completeness.
 *
 * // Feature: venue-data-capture, Property 2: API Venue Deserialization Completeness
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b></p>
 *
 * <p>For any JSON object containing a venue with an arbitrarily structured city sub-object
 * (with any combination of present/absent/null name, state, stateCode fields), deserializing
 * into ApiVenue SHALL produce a record where each present non-null field is accessible and
 * each absent/null field is null, without throwing an exception.</p>
 */
class ApiVenueDeserializationPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 2: Deserializing any valid venue JSON structure does not throw exceptions,
     * and all present non-null fields are accessible.
     *
     * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b></p>
     */
    @Property(tries = 100)
    void deserializationHandlesAnyCityStructureWithoutException(
            @ForAll("venueJsonProvider") VenueJsonInput input
    ) throws Exception {
        // Deserialize — must not throw
        SetlistFmApiClient.ApiVenue venue = objectMapper.readValue(
                input.json(), SetlistFmApiClient.ApiVenue.class);

        // Venue itself should never be null (we're deserializing a valid JSON object)
        assertNotNull(venue);

        // Verify field accessibility based on what was provided
        switch (input.cityVariant()) {
            case FULL_CITY -> {
                assertNotNull(venue.city());
                // Non-null fields should be accessible (not throw)
                if (input.cityName() != null) {
                    assertEquals(input.cityName(), venue.city().name());
                }
                if (input.state() != null) {
                    assertEquals(input.state(), venue.city().state());
                }
                if (input.stateCode() != null) {
                    assertEquals(input.stateCode(), venue.city().stateCode());
                }
            }
            case CITY_WITH_SOME_NULL_FIELDS -> {
                assertNotNull(venue.city());
                // Null fields should be null
                if (input.cityName() == null) {
                    assertNull(venue.city().name());
                } else {
                    assertEquals(input.cityName(), venue.city().name());
                }
                if (input.state() == null) {
                    assertNull(venue.city().state());
                } else {
                    assertEquals(input.state(), venue.city().state());
                }
                if (input.stateCode() == null) {
                    assertNull(venue.city().stateCode());
                } else {
                    assertEquals(input.stateCode(), venue.city().stateCode());
                }
            }
            case CITY_WITH_MISSING_FIELDS -> {
                assertNotNull(venue.city());
                // Missing fields in JSON should deserialize as null
                if (!input.includeCityName()) {
                    assertNull(venue.city().name());
                }
                if (!input.includeState()) {
                    assertNull(venue.city().state());
                }
                if (!input.includeStateCode()) {
                    assertNull(venue.city().stateCode());
                }
            }
            case NO_CITY_FIELD -> {
                assertNull(venue.city());
            }
            case CITY_EXPLICITLY_NULL -> {
                assertNull(venue.city());
            }
        }

        // Venue id and name should always be accessible
        assertEquals(input.venueId(), venue.id());
        assertEquals(input.venueName(), venue.name());
    }

    // ── Test input record ────────────────────────────────────────────────

    enum CityVariant {
        FULL_CITY,
        CITY_WITH_SOME_NULL_FIELDS,
        CITY_WITH_MISSING_FIELDS,
        NO_CITY_FIELD,
        CITY_EXPLICITLY_NULL
    }

    record VenueJsonInput(
            String json,
            CityVariant cityVariant,
            String venueId,
            String venueName,
            String cityName,
            String state,
            String stateCode,
            boolean includeCityName,
            boolean includeState,
            boolean includeStateCode
    ) {}

    // ── Arbitraries ──────────────────────────────────────────────────────

    @Provide
    Arbitrary<VenueJsonInput> venueJsonProvider() {
        Arbitrary<String> venueIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> venueNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> cityNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<String> states = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<String> stateCodes = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5);
        Arbitrary<CityVariant> variants = Arbitraries.of(CityVariant.values());

        return Combinators.combine(venueIds, venueNames, cityNames, states, stateCodes, variants)
                .as((venueId, venueName, cityName, state, stateCode, variant) -> {
                    StringBuilder json = new StringBuilder();
                    json.append("{");
                    json.append("\"id\":\"").append(escapeJson(venueId)).append("\",");
                    json.append("\"name\":\"").append(escapeJson(venueName)).append("\"");

                    boolean includeCityName = true;
                    boolean includeState = true;
                    boolean includeStateCode = true;
                    String actualCityName = cityName;
                    String actualState = state;
                    String actualStateCode = stateCode;

                    switch (variant) {
                        case FULL_CITY -> {
                            json.append(",\"city\":{");
                            json.append("\"name\":\"").append(escapeJson(cityName)).append("\",");
                            json.append("\"state\":\"").append(escapeJson(state)).append("\",");
                            json.append("\"stateCode\":\"").append(escapeJson(stateCode)).append("\"");
                            json.append("}");
                        }
                        case CITY_WITH_SOME_NULL_FIELDS -> {
                            // Randomly null out some fields
                            json.append(",\"city\":{");
                            boolean first = true;
                            // Make at least one field null — use a simple deterministic approach
                            // based on the hash of the inputs
                            int hash = (venueId + venueName).hashCode();
                            boolean nullName = (hash & 1) != 0;
                            boolean nullState = (hash & 2) != 0;
                            boolean nullStateCode = (hash & 4) != 0;
                            // Ensure at least one is null
                            if (!nullName && !nullState && !nullStateCode) {
                                nullName = true;
                            }

                            if (nullName) {
                                actualCityName = null;
                                if (!first) json.append(",");
                                json.append("\"name\":null");
                                first = false;
                            } else {
                                if (!first) json.append(",");
                                json.append("\"name\":\"").append(escapeJson(cityName)).append("\"");
                                first = false;
                            }
                            if (nullState) {
                                actualState = null;
                                if (!first) json.append(",");
                                json.append("\"state\":null");
                            } else {
                                if (!first) json.append(",");
                                json.append("\"state\":\"").append(escapeJson(state)).append("\"");
                            }
                            if (nullStateCode) {
                                actualStateCode = null;
                                if (!first) json.append(",");
                                json.append("\"stateCode\":null");
                            } else {
                                if (!first) json.append(",");
                                json.append("\"stateCode\":\"").append(escapeJson(stateCode)).append("\"");
                            }
                            json.append("}");
                        }
                        case CITY_WITH_MISSING_FIELDS -> {
                            // Some fields simply absent from JSON
                            json.append(",\"city\":{");
                            int hash2 = (venueId + stateCode).hashCode();
                            includeCityName = (hash2 & 1) == 0;
                            includeState = (hash2 & 2) == 0;
                            includeStateCode = (hash2 & 4) == 0;

                            boolean first = true;
                            if (includeCityName) {
                                json.append("\"name\":\"").append(escapeJson(cityName)).append("\"");
                                first = false;
                            } else {
                                actualCityName = null;
                            }
                            if (includeState) {
                                if (!first) json.append(",");
                                json.append("\"state\":\"").append(escapeJson(state)).append("\"");
                                first = false;
                            } else {
                                actualState = null;
                            }
                            if (includeStateCode) {
                                if (!first) json.append(",");
                                json.append("\"stateCode\":\"").append(escapeJson(stateCode)).append("\"");
                            } else {
                                actualStateCode = null;
                            }
                            json.append("}");
                        }
                        case NO_CITY_FIELD -> {
                            // No city field at all in the JSON
                            actualCityName = null;
                            actualState = null;
                            actualStateCode = null;
                            includeCityName = false;
                            includeState = false;
                            includeStateCode = false;
                        }
                        case CITY_EXPLICITLY_NULL -> {
                            json.append(",\"city\":null");
                            actualCityName = null;
                            actualState = null;
                            actualStateCode = null;
                            includeCityName = false;
                            includeState = false;
                            includeStateCode = false;
                        }
                    }

                    json.append("}");

                    return new VenueJsonInput(
                            json.toString(),
                            variant,
                            venueId,
                            venueName,
                            actualCityName,
                            actualState,
                            actualStateCode,
                            includeCityName,
                            includeState,
                            includeStateCode
                    );
                });
    }

    private static String escapeJson(String value) {
        if (value == null) return "null";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
