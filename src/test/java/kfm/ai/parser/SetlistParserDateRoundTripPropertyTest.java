package kfm.ai.parser;

import net.jqwik.api.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for date extraction round-trip in {@link SetlistParser}.
 *
 * // Feature: setlist-html-parser, Property 1: Date extraction round-trip
 *
 * <p>Validates: Requirements 1.1, 1.2</p>
 *
 * <p>For any valid date string (in {@code yyyy-MM-dd} or ISO datetime format), a synthetic
 * Document built with that datetime attribute value, when parsed, SHALL produce a {@link kfm.ai.types.SetList}
 * whose {@code date} field encodes exactly the same calendar date, with the time portion set
 * to midnight when no time component was present.</p>
 */
class SetlistParserDateRoundTripPropertyTest {

    // Initialized as a field — jqwik creates a fresh instance per property, so direct
    // field initialization is the correct approach (JUnit @BeforeEach does not run for @Property).
    private final SetlistParser parser = new SetlistParser();

    /**
     * Property 1 (date-only): For any valid {@code yyyy-MM-dd} date, building a Document
     * with {@code <time datetime="yyyy-MM-dd">} and parsing it SHALL produce a
     * {@link LocalDateTime} equal to {@code LocalDate.of(y, m, d).atStartOfDay()}.
     *
     * <p>Validates: Requirements 1.1, 1.2</p>
     */
    @Property(tries = 200)
    void dateOnlyRoundTrip(
            @ForAll("years") int year,
            @ForAll("months") int month,
            @ForAll("days") int day
    ) {
        // Build yyyy-MM-dd string
        String dateStr = String.format("%04d-%02d-%02d", year, month, day);

        // Build a synthetic Document with <time datetime="yyyy-MM-dd">
        String html = "<html><body><time datetime=\"" + dateStr + "\">" + dateStr + "</time></body></html>";
        Document doc = Jsoup.parse(html);

        LocalDateTime result = parser.parse(doc).getDate();

        // WHEN datetime contains only a date, time SHALL be midnight
        LocalDateTime expected = LocalDate.of(year, month, day).atStartOfDay();
        assertEquals(expected, result,
                "Date-only round-trip failed for: " + dateStr);
    }

    /**
     * Property 1 (full datetime): For any valid {@code yyyy-MM-ddTHH:mm:ss} datetime,
     * building a Document with that value and parsing it SHALL produce a {@link LocalDateTime}
     * equal to {@code LocalDateTime.of(y, m, d, h, min, 0)}.
     *
     * <p>Validates: Requirements 1.1, 1.2</p>
     */
    @Property(tries = 200)
    void fullDatetimeRoundTrip(
            @ForAll("years") int year,
            @ForAll("months") int month,
            @ForAll("days") int day,
            @ForAll("hours") int hour,
            @ForAll("minutes") int minute
    ) {
        // Build yyyy-MM-ddTHH:mm:ss string
        String datetimeStr = String.format("%04d-%02d-%02dT%02d:%02d:00", year, month, day, hour, minute);

        // Build a synthetic Document with <time datetime="yyyy-MM-ddTHH:mm:ss">
        String html = "<html><body><time datetime=\"" + datetimeStr + "\">" + datetimeStr + "</time></body></html>";
        Document doc = Jsoup.parse(html);

        LocalDateTime result = parser.parse(doc).getDate();

        // WHEN datetime contains a full timestamp, result SHALL equal that timestamp
        LocalDateTime expected = LocalDateTime.of(year, month, day, hour, minute, 0);
        assertEquals(expected, result,
                "Full datetime round-trip failed for: " + datetimeStr);
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /** Generates years in range [2000, 2030]. */
    @Provide
    Arbitrary<Integer> years() {
        return Arbitraries.integers().between(2000, 2030);
    }

    /** Generates months in range [1, 12]. */
    @Provide
    Arbitrary<Integer> months() {
        return Arbitraries.integers().between(1, 12);
    }

    /**
     * Generates days in range [1, 28] — safe for all months (avoids Feb 29 and
     * month-end boundary issues without needing calendar-aware validation).
     */
    @Provide
    Arbitrary<Integer> days() {
        return Arbitraries.integers().between(1, 28);
    }

    /** Generates hours in range [0, 23]. */
    @Provide
    Arbitrary<Integer> hours() {
        return Arbitraries.integers().between(0, 23);
    }

    /** Generates minutes in range [0, 59]. */
    @Provide
    Arbitrary<Integer> minutes() {
        return Arbitraries.integers().between(0, 59);
    }
}
