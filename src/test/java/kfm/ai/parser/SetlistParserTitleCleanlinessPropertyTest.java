package kfm.ai.parser;

import kfm.ai.types.Song;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for song title cleanliness in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 7: Song title is trimmed and excludes annotation and segue text
 *
 * <p>Validates: Requirements 2.3, 8.4, 9.4</p>
 *
 * <p>For any song entry whose source HTML title anchor contains surrounding whitespace,
 * and/or whose entry contains annotation text in parentheses, and/or a {@code >} segue marker,
 * the {@code title} field of the resulting {@link Song} SHALL equal the song name text with all
 * leading/trailing whitespace removed and with no annotation text or {@code >} characters included.</p>
 */
class SetlistParserTitleCleanlinessPropertyTest {

    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 7: Song title is trimmed and excludes annotation and segue text.
     *
     * <p>Validates: Requirements 2.3, 8.4, 9.4</p>
     */
    @Property(tries = 200)
    void titleExcludesAnnotationAndSegue(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String songName,
            @ForAll("whitespace") String leadingWs,
            @ForAll("whitespace") String trailingWs,
            @ForAll boolean hasAnnotation,
            @ForAll boolean hasSegue
    ) {
        // Build the song anchor with optional surrounding whitespace
        String anchorContent = leadingWs + songName + trailingWs;

        // Build optional annotation span
        String annotationHtml = hasAnnotation
                ? "<span class=\"songInfo\">(some performance note)</span>"
                : "";

        // Build optional segue span
        String segueHtml = hasSegue
                ? "<span class=\"segue\">&gt;</span>"
                : "";

        // Assemble full synthetic HTML
        String html = "<html><body>"
                + "<time datetime=\"2024-01-01\">Jan 1, 2024</time>"
                + "<div class=\"setlist-set\">"
                + "<ol>"
                + "<li class=\"song\">"
                + "<a>" + anchorContent + "</a>"
                + annotationHtml
                + segueHtml
                + "</li>"
                + "</ol>"
                + "</div>"
                + "</body></html>";

        Document doc = Jsoup.parse(html);
        Song song = parser.parse(doc).getSongSets().get(0).getSongs().get(0);

        // Title should equal the clean song name, trimmed
        assertEquals(songName, song.getTitle(),
                "Title should be trimmed song name without annotation or segue. "
                        + "Input anchor: '" + anchorContent + "'");

        // Title must not contain '>' character
        assertFalse(song.getTitle().contains(">"),
                "Title must not contain '>' segue character");

        // Title must not contain '(' annotation marker
        assertFalse(song.getTitle().contains("("),
                "Title must not contain '(' annotation marker");
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates optional whitespace strings (empty, spaces, tabs, or combinations).
     */
    @Provide
    Arbitrary<String> whitespace() {
        return Arbitraries.of("", " ", "  ", "\t", " \t ", "   ");
    }
}
