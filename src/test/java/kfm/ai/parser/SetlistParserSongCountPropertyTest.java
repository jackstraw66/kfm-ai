package kfm.ai.parser;

import kfm.ai.types.SetList;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Property-based tests for the song count invariant in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 4: Song count invariant
 *
 * <p>Validates: Requirements 2.2, 5.2</p>
 *
 * <p>For any synthetic Document where set block i contains K_i songs, the total song count
 * across all {@code SongSet} objects in the parsed {@link SetList} SHALL equal the sum
 * K_1 + K_2 + … + K_N.</p>
 */
class SetlistParserSongCountPropertyTest {

    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 4: Song count invariant.
     *
     * <p>For any list of integers representing songs-per-set counts, a synthetic Document
     * built with N set blocks where set i contains songsPerSet[i] songs, when parsed,
     * SHALL produce a {@link SetList} whose total song count across all SongSet objects
     * equals the sum of songsPerSet values.</p>
     *
     * <p>Validates: Requirements 2.2, 5.2</p>
     */
    @Property(tries = 200)
    void songCountMatchesEntries(@ForAll @Size(max = 10) List<@IntRange(min = 0, max = 5) Integer> songsPerSet) {
        // Build a synthetic Document with N set blocks, each containing M_i songs
        String html = buildHtmlWithSongs(songsPerSet);
        Document doc = Jsoup.parse(html);

        SetList result = parser.parse(doc);

        // songSets list must be non-null
        assertNotNull(result.getSongSets(), "songSets list must not be null");

        // Total song count across all SongSet objects must equal sum of songsPerSet
        int expectedTotal = songsPerSet.stream().mapToInt(Integer::intValue).sum();
        int actualTotal = result.getSongSets().stream()
                .mapToInt(s -> s.getSongs().size())
                .sum();

        assertEquals(expectedTotal, actualTotal,
                "Expected total song count " + expectedTotal + " but got " + actualTotal
                        + " for songsPerSet=" + songsPerSet);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a minimal setlist.fm-like HTML string with a valid {@code <time datetime>}
     * element and N {@code <div class="setlist-set">} blocks, where set i contains
     * {@code songsPerSet.get(i)} song entries.
     *
     * @param songsPerSet list of song counts per set
     * @return HTML string suitable for {@code Jsoup.parse()}
     */
    private String buildHtmlWithSongs(List<Integer> songsPerSet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<time datetime=\"2024-01-01\">January 1, 2024</time>");

        for (int setIndex = 0; setIndex < songsPerSet.size(); setIndex++) {
            sb.append("<div class=\"setlist-set\">");
            sb.append("<h2>Set ").append(setIndex + 1).append("</h2>");
            sb.append("<ol>");
            int songCount = songsPerSet.get(setIndex);
            for (int songIndex = 1; songIndex <= songCount; songIndex++) {
                sb.append("<li class=\"song\"><a>Song ")
                        .append(setIndex + 1).append("-").append(songIndex)
                        .append("</a></li>");
            }
            sb.append("</ol>");
            sb.append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }
}
