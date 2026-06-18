package kfm.ai.parser;

import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for annotation extraction in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 8: Annotation field matches source parenthetical text
 *
 * <p><b>Validates: Requirements 5.5, 8.2, 8.3, 8.5</b></p>
 *
 * <p>For any song entry, the {@code annotation} field SHALL be non-null (and equal to the
 * concatenated parenthetical content, without surrounding parentheses) if and only if at least
 * one parenthetical phrase was present in the source entry. Multiple phrases SHALL be joined
 * with a single space in DOM order.</p>
 */
class SetlistParserAnnotationPropertyTest {

    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 8: When annotation phrases are present in {@code .songInfo} spans, the parsed
     * {@code annotation} field equals the phrases joined by a single space. When no phrases
     * are present, {@code annotation} is null.
     *
     * <p><b>Validates: Requirements 5.5, 8.2, 8.3, 8.5</b></p>
     */
    @Property(tries = 200)
    void annotationMatchesParenthetical(
            @ForAll @Size(min = 0, max = 3) List<@AlphaChars @StringLength(min = 1, max = 20) String> phrases
    ) {
        // Build synthetic HTML with 0–3 annotation spans
        StringBuilder songInfoSpans = new StringBuilder();
        for (String phrase : phrases) {
            songInfoSpans.append("<span class=\"songInfo\">(").append(phrase).append(")</span>");
        }

        String html = "<html><body>"
                + "<time datetime=\"2024-01-01\">Jan 1, 2024</time>"
                + "<div class=\"setlist-set\">"
                + "<ol>"
                + "<li class=\"song\"><a>Song</a>" + songInfoSpans + "</li>"
                + "</ol>"
                + "</div>"
                + "</body></html>";

        Document doc = Jsoup.parse(html);
        SetList setList = parser.parse(doc);

        Song song = setList.getSongSets().get(0).getSongs().get(0);

        if (phrases.isEmpty()) {
            assertNull(song.getAnnotation(),
                    "annotation should be null when no parenthetical phrases are present");
        } else {
            String expected = String.join(" ", phrases);
            assertNotNull(song.getAnnotation(),
                    "annotation should be non-null when parenthetical phrases are present");
            assertEquals(expected, song.getAnnotation(),
                    "annotation should equal phrases joined by single space in DOM order");
        }
    }
}
