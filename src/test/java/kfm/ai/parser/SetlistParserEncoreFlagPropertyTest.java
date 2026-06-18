package kfm.ai.parser;

import kfm.ai.types.SetList;
import kfm.ai.types.SongSet;
import net.jqwik.api.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for encore flag assignment in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 6: Encore flag matches label pattern
 *
 * <p>Validates: Requirements 3.3, 3.5, 5.4, 7.2, 7.3</p>
 *
 * <p>For any synthetic Document containing set blocks with arbitrary label strings,
 * the {@code encore} field of each resulting {@link SongSet} SHALL be {@code true}
 * if and only if the corresponding label matches {@code ^Encore.*} (case-insensitive),
 * and {@code false} for all other labels.</p>
 */
class SetlistParserEncoreFlagPropertyTest {

    // Initialized as a field — jqwik creates a fresh instance per property, so direct
    // field initialization is the correct approach (JUnit @BeforeEach does not run for @Property).
    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 6: For any list of label strings (mixing encore and regular labels),
     * each parsed {@link SongSet}'s {@code encore} flag SHALL equal
     * {@code label.matches("(?i)^Encore.*")}.
     *
     * <p>Validates: Requirements 3.3, 3.5, 5.4, 7.2, 7.3</p>
     */
    @Property(tries = 200)
    void encoreFlagMatchesLabelPattern(
            @ForAll("mixedLabelLists") List<String> labels
    ) {
        // Build HTML with a <div class="setlist-set"><h2>{label}</h2></div> for each label
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<time datetime=\"2024-05-10\">May 10, 2024</time>");
        for (String label : labels) {
            html.append("<div class=\"setlist-set\"><h2>")
                .append(label)
                .append("</h2></div>");
        }
        html.append("</body></html>");

        Document doc = Jsoup.parse(html.toString());
        SetList setList = parser.parse(doc);
        List<SongSet> songSets = setList.getSongSets();

        assertEquals(labels.size(), songSets.size(),
                "SongSet count must match label count");

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            SongSet sset = songSets.get(i);
            boolean expectedEncore = label.matches("(?i)^Encore.*");
            assertEquals(expectedEncore, sset.isEncore(),
                    "encore flag mismatch for label \"" + label + "\" at index " + i
                    + ": expected " + expectedEncore + " but got " + sset.isEncore());
        }
    }

    /**
     * Property 6 (single label): For any single label string, a Document with one set block
     * SHALL produce exactly one SongSet whose encore flag matches the label pattern.
     *
     * <p>Validates: Requirements 3.3, 7.2, 7.3</p>
     */
    @Property(tries = 200)
    void singleLabelEncoreFlagMatchesPattern(
            @ForAll("anyLabel") String label
    ) {
        String html = "<html><body>"
                + "<time datetime=\"2024-05-10\">May 10, 2024</time>"
                + "<div class=\"setlist-set\"><h2>" + label + "</h2></div>"
                + "</body></html>";

        Document doc = Jsoup.parse(html);
        SetList setList = parser.parse(doc);
        List<SongSet> songSets = setList.getSongSets();

        assertEquals(1, songSets.size(), "Expected exactly one SongSet for label: " + label);

        SongSet sset = songSets.get(0);
        boolean expectedEncore = label.matches("(?i)^Encore.*");
        assertEquals(expectedEncore, sset.isEncore(),
                "encore flag mismatch for label \"" + label + "\": expected " + expectedEncore
                + " but got " + sset.isEncore());
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates a non-empty list of label strings that is a mix of encore labels
     * (e.g. "Encore", "Encore 1", "encore", "ENCORE", "encore 2") and regular labels
     * (e.g. "Set 1", "Set 2", "Main Set", "").
     */
    @Provide
    Arbitrary<List<String>> mixedLabelLists() {
        Arbitrary<String> label = Arbitraries.oneOf(encoreLabels(), regularLabels());
        return label.list().ofMinSize(1).ofMaxSize(8);
    }

    /**
     * Generates a single label that is either an encore label or a regular label.
     */
    @Provide
    Arbitrary<String> anyLabel() {
        return Arbitraries.oneOf(encoreLabels(), regularLabels());
    }

    /**
     * Generates encore-style label strings that match {@code ^Encore.*} case-insensitively,
     * covering mixed-case and suffixed variants.
     */
    @Provide
    Arbitrary<String> encoreLabels() {
        // Base prefixes that case-insensitively start with "Encore"
        Arbitrary<String> prefix = Arbitraries.of(
                "Encore", "encore", "ENCORE", "Encore:", "Encore 1", "Encore 2",
                "encore 1", "ENCORE 2", "Encore III", "encore set"
        );
        return prefix;
    }

    /**
     * Generates regular (non-encore) label strings that do NOT match {@code ^Encore.*},
     * including typical setlist labels and empty strings.
     */
    @Provide
    Arbitrary<String> regularLabels() {
        return Arbitraries.of(
                "Set 1", "Set 2", "Set 3", "Main Set", "", "Acoustic Set",
                "Electric Set", "Soundcheck", "Pre-Show", "Set I", "Set II"
        );
    }
}
