package kfm.ai.parser;

import kfm.ai.types.SetList;
import kfm.ai.types.SongSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SetlistParser} set-parsing and ordinal-assignment behaviour.
 *
 * <p>All tests use synthetic {@link Document} instances built with
 * {@link Jsoup#parse(String)} — no HTTP calls are made.</p>
 *
 * <p>A {@code <time datetime="2024-05-10">} element is included at document level
 * in every test so that date parsing succeeds and set parsing can be exercised in
 * isolation.</p>
 *
 * <p>Requirements covered: 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.4</p>
 */
class SetlistParserSetParsingTest {

    private static final String DATE_ELEMENT = "<time datetime=\"2024-05-10\">May 10, 2024</time>";

    private SetlistParser parser;

    @BeforeEach
    void setUp() {
        parser = new SetlistParser();
    }

    /**
     * Requirements 2.5 / 4.1 — A set block with no song entries SHALL produce
     * a {@code SongSet} with an empty (non-null) {@code songs} list and ordinal 1.
     */
    @Test
    void parse_empty_set_block() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Set 1</h2>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        assertEquals(1, result.getSongSets().size());
        SongSet set = result.getSongSets().get(0);
        assertNotNull(set.getSongs(), "songs list must not be null");
        assertTrue(set.getSongs().isEmpty(), "songs list must be empty");
        assertEquals(1, set.getOrdinal());
    }

    /**
     * Requirements 2.1 / 4.2 — A document with a date but no set blocks SHALL
     * return a {@code SetList} with an empty (non-null) {@code songSets} list.
     */
    @Test
    void parse_no_set_blocks() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        assertNotNull(result.getSongSets(), "songSets must not be null");
        assertTrue(result.getSongSets().isEmpty(), "songSets must be empty when no set blocks present");
    }

    /**
     * Requirements 3.3 / 4.4 — A set block with an unrecognised label SHALL have
     * {@code encore=false} and SHALL be assigned the next sequential ordinal.
     */
    @Test
    void parse_unrecognised_label() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Special</h2>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        assertEquals(1, result.getSongSets().size());
        SongSet set = result.getSongSets().get(0);
        assertFalse(set.isEncore(), "encore must be false for unrecognised label");
        assertEquals(1, set.getOrdinal());
    }

    /**
     * Requirements 3.3 / 7.2 — A set block labeled "Encore" SHALL produce a
     * {@code SongSet} with {@code encore=true}.
     */
    @Test
    void parse_encore_flag_set() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\">" +
                "  <h2>Encore</h2>" +
                "</div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        assertEquals(1, result.getSongSets().size());
        SongSet encore = result.getSongSets().get(0);
        assertTrue(encore.isEncore(), "encore must be true for 'Encore' label");
    }

    /**
     * Requirements 3.1 / 3.2 — Two regular sets followed by one encore SHALL produce
     * ordinals [1, 2, 3]; the counter SHALL NOT reset at the encore boundary.
     */
    @Test
    void parse_encore_ordinal_continues() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                DATE_ELEMENT +
                "<div class=\"setlist-set\"><h2>Set 1</h2></div>" +
                "<div class=\"setlist-set\"><h2>Set 2</h2></div>" +
                "<div class=\"setlist-set\"><h2>Encore</h2></div>" +
                "</body></html>"
        );

        SetList result = parser.parse(doc);

        List<SongSet> sets = result.getSongSets();
        assertEquals(3, sets.size());
        assertEquals(1, sets.get(0).getOrdinal());
        assertEquals(2, sets.get(1).getOrdinal());
        assertEquals(3, sets.get(2).getOrdinal());
        assertFalse(sets.get(0).isEncore());
        assertFalse(sets.get(1).isEncore());
        assertTrue(sets.get(2).isEncore());
    }
}
