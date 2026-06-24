package kfm.ai.ingest;

import kfm.ai.dao.SetListRepository;
import kfm.ai.types.SetList;
import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for venue field persistence round-trip.
 *
 * // Feature: venue-data-capture, Property 1: Venue Field Persistence Round-Trip
 *
 * <p><b>Validates: Requirements 2.5</b></p>
 *
 * <p>For any valid SetList entity with non-null venue fields (venueName, city, state
 * containing arbitrary non-blank strings within column length limits), persisting the
 * entity and then loading it by ID SHALL return an entity with identical venueName,
 * city, and state values.</p>
 */
@JqwikSpringSupport
@SpringBootTest(properties = {
        "spring.ai.ollama.chat.enabled=false",
        "spring.ai.ollama.init.pull-model-strategy=never"
})
@Import(MySqlTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
        "batch.ingest.request-delay-ms=100",
        "batch.ingest.index-url=http://localhost"
})
class VenueFieldPersistenceRoundTripPropertyTest {

    @Autowired
    private SetListRepository setListRepository;

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * Property 1: Venue Field Persistence Round-Trip
     *
     * For any non-blank strings within column length limits for venueName (1-512),
     * city (1-255), and state (1-100), persisting a SetList entity and reloading
     * it by ID returns identical venue field values.
     *
     * <p><b>Validates: Requirements 2.5</b></p>
     */
    @Property(tries = 100)
    void venueFieldsSurvivePersistenceRoundTrip(
            @ForAll("venueNames") String venueName,
            @ForAll("cityNames") String cityName,
            @ForAll("stateValues") String state
    ) {
        // Generate unique date and sourceUrl per trial to avoid unique constraint violations
        int counter = COUNTER.incrementAndGet();
        LocalDateTime uniqueDate = LocalDateTime.of(2000, 1, 1, 0, 0).plusMinutes(counter);
        String uniqueSourceUrl = "https://www.setlist.fm/setlist/test/" + UUID.randomUUID() + ".html";

        // Build entity with venue fields
        SetList setList = SetList.builder()
                .date(uniqueDate)
                .sourceUrl(uniqueSourceUrl)
                .venueName(venueName)
                .city(cityName)
                .state(state)
                .songSets(new ArrayList<>())
                .build();

        // Persist
        SetList saved = setListRepository.saveAndFlush(setList);
        assertThat(saved.getId()).isNotNull();

        // Reload by ID
        SetList reloaded = setListRepository.findById(saved.getId()).orElseThrow();

        // Assert venue fields are preserved
        assertThat(reloaded.getVenueName()).isEqualTo(venueName);
        assertThat(reloaded.getCity()).isEqualTo(cityName);
        assertThat(reloaded.getState()).isEqualTo(state);
    }

    /**
     * Generates random non-blank strings within the venueName column limit (1-512 chars).
     */
    @Provide
    Arbitrary<String> venueNames() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(512)
                .filter(s -> !s.isBlank());
    }

    /**
     * Generates random non-blank strings within the city column limit (1-255 chars).
     */
    @Provide
    Arbitrary<String> cityNames() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(255)
                .filter(s -> !s.isBlank());
    }

    /**
     * Generates random non-blank strings within the state column limit (1-100 chars).
     */
    @Provide
    Arbitrary<String> stateValues() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.isBlank());
    }
}
