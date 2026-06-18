package kfm.ai.parser;

import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SetlistParser} song text extraction behaviour.
 *
 * <p>All tests use synthetic {@link Document} instances built with
 * {@link Jsoup#parse(String)} — no HTTP calls are made.</p>
 *
 * <p>A {@code <time datetime="2024-05-10">} element is included at document level
 * in every test so that date parsing succeeds and song parsing can be exercised in
 * isolation.</p>
 *
 * <p>Requirements covered: 2.3, 2.4, 8.2, 8.3, 8.4, 8.5, 9.2, 9.3, 9.4, 9.5</p>
 */
class SetlistParserSongExtractionTest {

    private static final String DATE_ELEMENT = "<time datetime=\"2024-05-10\">May 10, 2024</time>";

    private SetlistParser parser;

    @BeforeEach
    void setUp() {
        parser = new SetlistParser();
    }

    /**
     * Requirement 2.4 — Any parsed song SHALL have {@code lyrics == null}.
     */
    @Test
    void parse_null_lyrics() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\"><a href=\"/song\">Truckin'</a></li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertNull(song.getLyrics(), "lyrics must always be null from this parser");
    }

    /**
     * Requirement 2.3 — Song title SHALL have leading/trailing whitespace removed.
     */
    @Test
    void parse_song_title_trimmed() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\"><a href=\"/song\">  Fire on the Mountain  </a></li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertEquals("Fire on the Mountain", song.getTitle());
    }

    /**
     * Requirement 8.3 — Song entry with no parenthetical → {@code annotation == null}.
     */
    @Test
    void parse_song_no_annotation() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\"><a href=\"/song\">Sugar Magnolia</a></li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertNull(song.getAnnotation(), "annotation must be null when no parenthetical present");
    }

    /**
     * Requirement 8.2 — Song with one parenthetical phrase → {@code annotation} equals
     * the text without surrounding parentheses.
     */
    @Test
    void parse_song_single_annotation() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\">" +
                "    <a href=\"/song\">Scarlet Begonias</a>" +
                "    <span class=\"songInfo\">(First time since 2019)</span>" +
                "  </li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertEquals("First time since 2019", song.getAnnotation());
    }

    /**
     * Requirement 8.5 — Song with two parenthetical phrases → joined by single space.
     */
    @Test
    void parse_multiple_annotations() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\">" +
                "    <a href=\"/song\">Dark Star</a>" +
                "    <span class=\"songInfo\">(with Jerry)</span>" +
                "    <span class=\"songInfo\">(extended jam)</span>" +
                "  </li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertEquals("with Jerry extended jam", song.getAnnotation());
    }

    /**
     * Requirement 9.2 — Song entry with {@code >} segue marker → {@code segue == true}.
     */
    @Test
    void parse_segue_marker_present() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\">" +
                "    <a href=\"/song\">China Cat Sunflower</a>" +
                "    <span class=\"segue\">&gt;</span>" +
                "  </li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertTrue(song.isSegue(), "segue must be true when > marker present");
    }

    /**
     * Requirement 9.3 — Song entry without {@code >} marker → {@code segue == false}.
     */
    @Test
    void parse_segue_marker_absent() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\">" +
                "    <a href=\"/song\">Ripple</a>" +
                "  </li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertFalse(song.isSegue(), "segue must be false when no > marker present");
    }

    /**
     * Requirement 9.5 — Last song in set with {@code >} marker → {@code segue == true};
     * absence of a following song SHALL NOT suppress the segue flag.
     */
    @Test
    void parse_last_song_segue() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol>" +
                "    <li class=\"song\"><a href=\"/song\">Eyes of the World</a></li>" +
                "    <li class=\"song\">" +
                "      <a href=\"/song\">Drums</a>" +
                "      <span class=\"segue\">&gt;</span>" +
                "    </li>" +
                "  </ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song lastSong = result.getSongSets().get(0).getSongs().get(1);
        assertTrue(lastSong.isSegue(), "segue must be true on last song when > marker present");
    }

    /**
     * Requirement 8.4 — Title must not contain parenthetical annotation text.
     */
    @Test
    void parse_title_excludes_annotation() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\">" +
                "    <a href=\"/song\">Friend of the Devil</a>" +
                "    <span class=\"songInfo\">(acoustic)</span>" +
                "  </li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertFalse(song.getTitle().contains("("), "title must not contain '('");
        assertFalse(song.getTitle().contains(")"), "title must not contain ')'");
        assertFalse(song.getTitle().contains("acoustic"), "title must not contain annotation text");
        assertEquals("Friend of the Devil", song.getTitle());
    }

    /**
     * Requirement 9.4 — Title must not contain the {@code >} segue character.
     */
    @Test
    void parse_title_excludes_segue_char() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "  <ol><li class=\"song\">" +
                "    <a href=\"/song\">Scarlet Begonias</a>" +
                "    <span class=\"segue\">&gt;</span>" +
                "  </li></ol>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        Song song = result.getSongSets().get(0).getSongs().get(0);
        assertFalse(song.getTitle().contains(">"), "title must not contain '>'");
        assertEquals("Scarlet Begonias", song.getTitle());
    }
}
