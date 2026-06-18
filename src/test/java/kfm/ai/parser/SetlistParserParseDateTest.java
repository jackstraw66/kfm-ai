package kfm.ai.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SetlistParser} date-parsing behaviour.
 *
 * <p>All tests use synthetic {@link Document} instances built with
 * {@link Jsoup#parse(String)} — no HTTP calls are made.</p>
 *
 * <p>Requirements covered: 1.1, 1.2, 1.3, 1.4, 4.3, 10.4</p>
 */
class SetlistParserParseDateTest {

    private SetlistParser parser;

    @BeforeEach
    void setUp() {
        parser = new SetlistParser();
    }

    /**
     * Requirement 4.3 / 10.4 — null Document → IllegalArgumentException whose
     * message contains "Document input".
     */
    @Test
    void parse_null_document_throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(null)
        );
        assertTrue(ex.getMessage().contains("Document input"),
                "Expected message to contain 'Document input' but was: " + ex.getMessage());
    }

    /**
     * Requirement 1.3 — Document with no {@code <time datetime>} element →
     * SetlistParseException whose message contains "datetime".
     */
    @Test
    void parse_missing_datetime_throws() {
        Document doc = Jsoup.parse("<html><body><p>No time element here</p></body></html>");

        SetlistParseException ex = assertThrows(
                SetlistParseException.class,
                () -> parser.parse(doc)
        );
        assertTrue(ex.getMessage().contains("datetime"),
                "Expected message to contain 'datetime' but was: " + ex.getMessage());
    }

    /**
     * Requirement 1.1 / 1.2 — date-only datetime attribute → LocalDateTime at midnight.
     */
    @Test
    void parse_date_only_midnight() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                "<time datetime=\"2024-05-10\">May 10, 2024</time>" +
                "</body></html>"
        );

        LocalDateTime result = parser.parse(doc).getDate();

        assertEquals(LocalDateTime.of(2024, 5, 10, 0, 0, 0), result);
    }

    /**
     * Requirement 1.1 — full ISO datetime attribute → exact LocalDateTime.
     */
    @Test
    void parse_full_datetime() {
        Document doc = Jsoup.parse(
                "<html><body>" +
                "<time datetime=\"2024-05-10T20:30:00\">May 10, 2024</time>" +
                "</body></html>"
        );

        LocalDateTime result = parser.parse(doc).getDate();

        assertEquals(LocalDateTime.of(2024, 5, 10, 20, 30, 0), result);
    }
}
