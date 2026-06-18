package kfm.ai.parser;

import kfm.ai.types.SetList;
import net.jqwik.api.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Property-based tests for the set count invariant in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 3: Set count invariant
 *
 * <p>Validates: Requirements 2.1, 5.1, 4.2</p>
 *
 * <p>For any synthetic Document containing N set blocks (N ≥ 0), parsing SHALL produce a
 * {@link SetList} whose {@code songSets} list has exactly N elements.</p>
 */
class SetlistParserSetCountPropertyTest {

    // Initialized as a field — jqwik creates a fresh instance per property, so direct
    // field initialization is the correct approach (JUnit @BeforeEach does not run for @Property).
    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 3: Set count invariant.
     *
     * <p>For any N in [0, 10], a synthetic Document containing exactly N
     * {@code <div class="setlist-set">} blocks, when parsed, SHALL produce a {@link SetList}
     * whose {@code songSets} list has exactly N elements.</p>
     *
     * <p>Validates: Requirements 2.1, 5.1, 4.2</p>
     */
    @Property(tries = 200)
    void setCountMatchesBlocks(@ForAll("setBlockCounts") int n) {
        // Build a synthetic Document with exactly N set blocks and the required <time> element
        String html = buildHtmlWithNSets(n);
        Document doc = Jsoup.parse(html);

        SetList result = parser.parse(doc);

        // songSets list must be non-null
        assertNotNull(result.getSongSets(),
                "songSets list must not be null for N=" + n);

        // WHEN Document contains N set blocks, songSets.size() SHALL equal N
        assertEquals(n, result.getSongSets().size(),
                "Expected " + n + " SongSet(s) but got " + result.getSongSets().size()
                        + " for N=" + n);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a minimal setlist.fm-like HTML string with a valid {@code <time datetime>}
     * element and exactly {@code n} {@code <div class="setlist-set">} blocks.
     *
     * @param n the number of set blocks to include (0–10)
     * @return HTML string suitable for {@code Jsoup.parse()}
     */
    private String buildHtmlWithNSets(int n) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        // Required <time datetime> element for date parsing
        sb.append("<time datetime=\"2024-05-10\">May 10, 2024</time>");
        // Append N set blocks using the CSS selector used by SetlistParser: div.setlist-set
        for (int i = 1; i <= n; i++) {
            sb.append("<div class=\"setlist-set\">");
            sb.append("<h2>Set ").append(i).append("</h2>");
            sb.append("</div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates set block counts N in [0, 10] inclusive, covering the empty case,
     * a single set, and multi-set scenarios.
     */
    @Provide
    Arbitrary<Integer> setBlockCounts() {
        return Arbitraries.integers().between(0, 10);
    }
}
