package kfm.ai.parser;

import kfm.ai.types.SetList;
import kfm.ai.types.SongSet;
import net.jqwik.api.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for ordinal sequence correctness in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 5: Ordinal sequence is contiguous and gapless
 *
 * <p>Validates: Requirements 3.1, 3.2, 5.3</p>
 *
 * <p>For any synthetic Document containing N set blocks (with mixed encore and regular labels),
 * the {@code ordinal} values of the resulting {@link SongSet} objects SHALL be exactly
 * {@code [1, 2, …, N]} in DOM order, with no gaps, no duplicates, and no resets at encore
 * boundaries.</p>
 */
class SetlistParserOrdinalSequencePropertyTest {

    // Initialized as a field — jqwik creates a fresh instance per property, so direct
    // field initialization is the correct approach (JUnit @BeforeEach does not run for @Property).
    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 5: For any list of 1–8 set labels (each randomly either a regular label or
     * an encore label), building a synthetic Document with N {@code <div class="setlist-set">}
     * blocks and parsing it SHALL produce {@link SongSet} objects whose {@code ordinal} values
     * form the exact sequence {@code [1, 2, …, N]} in DOM order — no gaps, no duplicates,
     * no resets at encore boundaries.
     *
     * <p>Validates: Requirements 3.1, 3.2, 5.3</p>
     */
    @Property(tries = 200)
    void ordinalsAreContiguousAndGapless(
            @ForAll("setLabels") List<String> labels
    ) {
        int n = labels.size();

        // Build synthetic HTML with N set blocks and the required <time datetime> element
        String html = buildHtml(labels);
        Document doc = Jsoup.parse(html);

        SetList setList = parser.parse(doc);
        List<SongSet> songSets = setList.getSongSets();

        // Assert the number of song sets equals N
        assertEquals(n, songSets.size(),
                "Expected " + n + " SongSets but got " + songSets.size()
                        + " for labels: " + labels);

        // Assert ordinals form exactly [1, 2, ..., N] in DOM order
        List<Integer> actualOrdinals = songSets.stream()
                .map(SongSet::getOrdinal)
                .collect(Collectors.toList());

        List<Integer> expectedOrdinals = IntStream.rangeClosed(1, n)
                .boxed()
                .collect(Collectors.toList());

        assertEquals(expectedOrdinals, actualOrdinals,
                "Ordinal sequence must be [1.." + n + "] with no gaps, duplicates, or resets."
                        + " Labels: " + labels
                        + " | Actual ordinals: " + actualOrdinals);
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates a list of 1–8 label strings where each element is randomly either a
     * regular label (e.g., "Set 1", "Set 2") or an encore label (e.g., "Encore", "Encore 1").
     */
    @Provide
    Arbitrary<List<String>> setLabels() {
        Arbitrary<String> regularLabel = Arbitraries.integers()
                .between(1, 5)
                .map(i -> "Set " + i);

        Arbitrary<String> encoreLabel = Arbitraries.integers()
                .between(0, 3)
                .map(i -> i == 0 ? "Encore" : "Encore " + i);

        Arbitrary<String> anyLabel = Arbitraries.oneOf(regularLabel, encoreLabel);

        return anyLabel.list().ofMinSize(1).ofMaxSize(8);
    }

    // ── HTML building helpers ─────────────────────────────────────────────

    /**
     * Builds a minimal synthetic HTML document containing N set blocks.
     * Each block is a {@code <div class="setlist-set"><h2>{label}</h2></div>}.
     * A {@code <time datetime="2024-05-10">} element is included for date parsing.
     *
     * @param labels ordered list of set label strings
     * @return HTML string suitable for {@link Jsoup#parse(String)}
     */
    private String buildHtml(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<time datetime=\"2024-05-10\">May 10, 2024</time>");
        for (String label : labels) {
            sb.append("<div class=\"setlist-set\"><h2>")
              .append(label)
              .append("</h2></div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }
}
