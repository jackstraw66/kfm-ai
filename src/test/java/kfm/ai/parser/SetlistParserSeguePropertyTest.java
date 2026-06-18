package kfm.ai.parser;

import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for the segue flag in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 9: Segue flag matches source marker
 *
 * <p>Validates: Requirements 5.6, 9.2, 9.3, 9.5</p>
 *
 * <p>For any song entry, the {@code segue} field SHALL be {@code true} if and only if a
 * {@code >} segue marker was present adjacent to that entry in the source HTML, regardless
 * of whether a following song exists in the same set.</p>
 */
class SetlistParserSeguePropertyTest {

    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 9: For any list of songs with arbitrary segue flags (including the last song),
     * parsing SHALL produce songs whose {@code segue} field matches the corresponding flag.
     *
     * <p><b>Validates: Requirements 5.6, 9.2, 9.3, 9.5</b></p>
     */
    @Property(tries = 200)
    void segueFlagMatchesMarker(
            @ForAll @Size(min = 1, max = 5) List<Boolean> segueFlags
    ) {
        // Build synthetic HTML with songs that have or lack segue markers
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<time datetime=\"2024-01-01\">January 1, 2024</time>");
        html.append("<div class=\"setlist-set\">");
        html.append("<ol>");

        for (int i = 0; i < segueFlags.size(); i++) {
            html.append("<li class=\"song\">");
            html.append("<a>Song ").append(i).append("</a>");
            if (segueFlags.get(i)) {
                html.append("<span class=\"segue\">&gt;</span>");
            }
            html.append("</li>");
        }

        html.append("</ol>");
        html.append("</div>");
        html.append("</body></html>");

        Document doc = Jsoup.parse(html.toString());
        SetList setList = parser.parse(doc);

        List<Song> songs = setList.getSongSets().get(0).getSongs();

        assertEquals(segueFlags.size(), songs.size(),
                "Song count should match segue flags count");

        for (int i = 0; i < segueFlags.size(); i++) {
            assertEquals(segueFlags.get(i), songs.get(i).isSegue(),
                    "Song " + i + " segue flag mismatch: expected " + segueFlags.get(i));
        }
    }
}
