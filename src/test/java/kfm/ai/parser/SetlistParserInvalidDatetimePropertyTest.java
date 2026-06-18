package kfm.ai.parser;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for invalid datetime exception behavior in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 2: Invalid datetime strings produce SetlistParseException
 *
 * <p>Validates: Requirements 1.4</p>
 *
 * <p>For any string that is not a valid ISO date or datetime, when a Document is built with
 * that string as the {@code datetime} attribute and parsed, THE {@link SetlistParser} SHALL throw
 * a {@link SetlistParseException} whose message contains that exact string.</p>
 */
class SetlistParserInvalidDatetimePropertyTest {

    // Initialized as a field — jqwik creates a fresh instance per property, so direct
    // field initialization is the correct approach (JUnit @BeforeEach does not run for @Property).
    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 2: For any alphanumeric string that cannot be parsed as a valid ISO date or datetime,
     * building a Document with that string as the {@code datetime} attribute and parsing it SHALL
     * throw a {@link SetlistParseException} whose message contains the exact raw string.
     *
     * <p>Validates: Requirements 1.4</p>
     */
    @Property(tries = 200)
    void invalidDatetimeStringThrowsExceptionWithValueInMessage(
            @ForAll("invalidDateStrings") String rawDatetime
    ) {
        // Build a synthetic Document with the invalid datetime attribute
        String html = "<html><body><time datetime=\"" + rawDatetime + "\">" + rawDatetime + "</time></body></html>";
        Document doc = Jsoup.parse(html);

        // WHEN an invalid datetime is parsed, a SetlistParseException SHALL be thrown
        SetlistParseException exception = assertThrows(
                SetlistParseException.class,
                () -> parser.parse(doc),
                "Expected SetlistParseException for invalid datetime: " + rawDatetime
        );

        // AND its message SHALL contain the exact raw datetime string
        assertNotNull(exception.getMessage(),
                "Exception message must not be null for invalid datetime: " + rawDatetime);
        assertTrue(
                exception.getMessage().contains(rawDatetime),
                "Exception message \"" + exception.getMessage()
                        + "\" must contain the raw datetime value: \"" + rawDatetime + "\""
        );
    }

    /**
     * Property 2 (with word-like strings): Same invariant holds for strings that look
     * like words or sentences — these are clearly not valid ISO dates.
     *
     * <p>Validates: Requirements 1.4</p>
     */
    @Property(tries = 200)
    void wordLikeStringThrowsExceptionWithValueInMessage(
            @ForAll("wordLikeStrings") String rawDatetime
    ) {
        // Build a synthetic Document with the invalid datetime attribute
        String html = "<html><body><time datetime=\"" + rawDatetime + "\">" + rawDatetime + "</time></body></html>";
        Document doc = Jsoup.parse(html);

        // WHEN an invalid datetime is parsed, a SetlistParseException SHALL be thrown
        SetlistParseException exception = assertThrows(
                SetlistParseException.class,
                () -> parser.parse(doc),
                "Expected SetlistParseException for word-like datetime: " + rawDatetime
        );

        // AND its message SHALL contain the exact raw datetime string
        assertNotNull(exception.getMessage(),
                "Exception message must not be null for invalid datetime: " + rawDatetime);
        assertTrue(
                exception.getMessage().contains(rawDatetime),
                "Exception message \"" + exception.getMessage()
                        + "\" must contain the raw datetime value: \"" + rawDatetime + "\""
        );
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates strings that are definitely not valid ISO dates or datetimes.
     * Uses alphanumeric characters with length between 2 and 20, filtering out:
     * - Empty strings (different behavior)
     * - Strings that happen to be parseable as valid ISO dates or datetimes
     */
    @Provide
    Arbitrary<String> invalidDateStrings() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(2)
                .ofMaxLength(20)
                // Filter out any string that accidentally parses as a valid ISO date or datetime
                .filter(s -> !isValidIsoDate(s));
    }

    /**
     * Generates word-like strings (alpha only, at least 3 chars) that will never
     * be valid ISO dates. Strings containing only letters cannot match any date format.
     */
    @Provide
    Arbitrary<String> wordLikeStrings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(15);
    }

    /**
     * Returns {@code true} if the given string can be parsed as either a
     * {@code yyyy-MM-dd} date or an ISO {@code LocalDateTime}.
     */
    private boolean isValidIsoDate(String s) {
        // Try yyyy-MM-dd date format
        try {
            LocalDate.parse(s);
            return true;
        } catch (DateTimeParseException ignored) {
            // not a date-only string
        }
        // Try full ISO datetime
        try {
            LocalDateTime.parse(s);
            return true;
        } catch (DateTimeParseException ignored) {
            // not a datetime string
        }
        return false;
    }
}
