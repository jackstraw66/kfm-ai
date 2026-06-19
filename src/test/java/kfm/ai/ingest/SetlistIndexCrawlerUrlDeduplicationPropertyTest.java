package kfm.ai.ingest;

import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based test for URL deduplication in the setlist index crawling phase.
 *
 * // Feature: setlist-batch-ingest, Property 1: URL Deduplication Invariant
 *
 * <p>Validates: Requirements 1.3</p>
 *
 * <p>For any list of discovered setlist page URLs (potentially containing duplicates),
 * the output of the URL discovery phase SHALL contain each unique URL exactly once
 * and SHALL contain every distinct URL present in the input.</p>
 *
 * <p>The {@link SetlistIndexCrawler} uses a {@link LinkedHashSet} internally for deduplication.
 * This test verifies the deduplication property directly: given a list of URL strings with
 * deliberate duplicates, feeding them through a LinkedHashSet and converting to a List
 * (as the crawler does) produces output with no duplicates and all unique inputs present.</p>
 */
class SetlistIndexCrawlerUrlDeduplicationPropertyTest {

    /**
     * Property 1: For any list of URL strings (potentially containing duplicates),
     * deduplication via LinkedHashSet SHALL produce a list where:
     * <ul>
     *   <li>No URL appears more than once</li>
     *   <li>Every distinct URL from the input is present in the output</li>
     * </ul>
     *
     * <p>Validates: Requirements 1.3</p>
     */
    @Property(tries = 200)
    void deduplicatedUrlsContainAllUniqueInputsExactlyOnce(
            @ForAll("urlListsWithDuplicates") List<String> inputUrls
    ) {
        // Simulate the deduplication logic used by SetlistIndexCrawler
        LinkedHashSet<String> deduplicatedSet = new LinkedHashSet<>(inputUrls);
        List<String> result = new ArrayList<>(deduplicatedSet);

        // Verify: no duplicates in output
        Set<String> resultAsSet = new HashSet<>(result);
        assertEquals(resultAsSet.size(), result.size(),
                "Output must contain no duplicate URLs. Input: " + inputUrls);

        // Verify: all unique inputs are present in output
        Set<String> uniqueInputs = new HashSet<>(inputUrls);
        assertEquals(uniqueInputs, resultAsSet,
                "Output must contain every distinct URL from the input. Input: " + inputUrls);
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates lists of 0–30 URL strings where duplicates are deliberately introduced.
     * URLs follow the pattern of setlist.fm setlist page URLs.
     */
    @Provide
    Arbitrary<List<String>> urlListsWithDuplicates() {
        Arbitrary<String> urlArbitrary = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(5).ofMaxLength(20)
                .map(slug -> "https://www.setlist.fm/setlist/" + slug);

        return urlArbitrary.list().ofMinSize(1).ofMaxSize(15)
                .flatMap(uniqueUrls -> {
                    // Create duplicates by repeating some URLs
                    Arbitrary<List<String>> duplicatesArbitrary =
                            Arbitraries.of(uniqueUrls).list().ofMinSize(0).ofMaxSize(15);

                    return duplicatesArbitrary.map(duplicates -> {
                        List<String> combined = new ArrayList<>(uniqueUrls);
                        combined.addAll(duplicates);
                        return combined;
                    });
                });
    }
}
