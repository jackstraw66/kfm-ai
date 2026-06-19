package kfm.ai.ingest;

import kfm.ai.dao.SetListRepository;
import kfm.ai.types.SetList;
import net.jqwik.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for natural-key deduplication in the batch ingest pipeline.
 *
 * // Feature: setlist-batch-ingest, Property 5: Natural-Key Deduplication
 *
 * <p>Validates: Requirements 4.1, 4.2, 4.4, 7.4</p>
 *
 * <p>For any SetList whose {@code date} (at full LocalDateTime precision) OR whose
 * {@code sourceUrl} matches an existing record in the database, the batch service
 * SHALL skip persistence for that show. Conversely, for any SetList whose {@code date}
 * AND {@code sourceUrl} are both absent from the database, the show SHALL be persisted.</p>
 */
class BatchIngestNaturalKeyDeduplicationPropertyTest {

    /**
     * Property 5: When either existsByDate() or existsBySourceUrl() returns true,
     * the result SHALL be SKIPPED and save() SHALL NOT be called.
     * When both return false, the result SHALL be INGESTED and save() SHALL be called.
     *
     * <p>Validates: Requirements 4.1, 4.2, 4.4, 7.4</p>
     */
    @Property(tries = 200)
    void showsWithMatchingNaturalKeysAreSkippedAndNewShowsArePersisted(
            @ForAll("randomDates") LocalDateTime date,
            @ForAll("randomUrls") String sourceUrl,
            @ForAll("deduplicationScenario") DeduplicationScenario scenario
    ) {
        // Arrange: mock the repository
        SetListRepository repository = mock(SetListRepository.class);
        when(repository.existsByDate(any(LocalDateTime.class))).thenReturn(scenario.dateExists);
        when(repository.existsBySourceUrl(any(String.class))).thenReturn(scenario.urlExists);
        when(repository.save(any(SetList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShowPersistenceHelper helper = new ShowPersistenceHelper(repository);

        // Build a minimal SetList with the generated date and sourceUrl
        SetList setList = SetList.builder()
                .date(date)
                .sourceUrl(sourceUrl)
                .build();

        // Act
        PersistResult result = helper.persistShow(setList);

        // Assert
        if (scenario.dateExists || scenario.urlExists) {
            // Either natural key matches → SKIPPED, save() not called
            assertEquals(PersistResult.SKIPPED, result,
                    "Expected SKIPPED when dateExists=" + scenario.dateExists
                            + " or urlExists=" + scenario.urlExists);
            verify(repository, never()).save(any(SetList.class));
        } else {
            // Both keys are new → INGESTED, save() called
            assertEquals(PersistResult.INGESTED, result,
                    "Expected INGESTED when both dateExists=false and urlExists=false");
            verify(repository, times(1)).save(setList);
        }
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates random LocalDateTime values across a reasonable date range
     * (1965–2025 for Grateful Dead shows, but extended for property coverage).
     */
    @Provide
    Arbitrary<LocalDateTime> randomDates() {
        return Arbitraries.integers().between(1965, 2025).flatMap(year ->
                Arbitraries.integers().between(1, 12).flatMap(month ->
                        Arbitraries.integers().between(1, 28).flatMap(day ->
                                Arbitraries.integers().between(0, 23).flatMap(hour ->
                                        Arbitraries.integers().between(0, 59).map(minute ->
                                                LocalDateTime.of(year, month, day, hour, minute, 0)
                                        )
                                )
                        )
                )
        );
    }

    /**
     * Generates random URL strings following the setlist.fm URL pattern.
     */
    @Provide
    Arbitrary<String> randomUrls() {
        return Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(5).ofMaxLength(30)
                .map(slug -> "https://www.setlist.fm/setlist/" + slug + ".html");
    }

    /**
     * Generates all possible deduplication scenarios:
     * - dateExists=true, urlExists=true (skip)
     * - dateExists=true, urlExists=false (skip)
     * - dateExists=false, urlExists=true (skip)
     * - dateExists=false, urlExists=false (persist)
     */
    @Provide
    Arbitrary<DeduplicationScenario> deduplicationScenario() {
        return Arbitraries.of(
                new DeduplicationScenario(true, true),
                new DeduplicationScenario(true, false),
                new DeduplicationScenario(false, true),
                new DeduplicationScenario(false, false)
        );
    }

    /**
     * Simple record to hold a deduplication test scenario.
     */
    record DeduplicationScenario(boolean dateExists, boolean urlExists) {}
}
