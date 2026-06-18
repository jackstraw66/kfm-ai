package kfm.ai.parser;

import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import kfm.ai.types.SongSet;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring-managed component that parses a setlist.fm {@link Document} (produced by the
 * external {@code html-parser} library) into the {@link SetList} domain hierarchy.
 *
 * <p>This component is stateless and therefore safe for concurrent use as a singleton bean.
 * It does not perform any HTTP requests, URL resolution, or raw HTML string parsing;
 * those responsibilities belong to the {@code html-parser} library.</p>
 */
@Component
public class SetlistParser {

    /**
     * Parses a setlist.fm Document into the SetList domain hierarchy.
     *
     * @param document fully-parsed Jsoup Document from a setlist.fm page; must not be null
     * @return SetList populated with concert date, sets, and songs in DOM order
     * @throws IllegalArgumentException if document is null
     * @throws SetlistParseException    if the datetime element is missing or its value cannot be parsed
     */
    public SetList parse(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document input must not be null");
        }

        LocalDateTime date = parseDate(document);
        List<SongSet> sets = parseSets(document);

        return SetList.builder()
                .date(date)
                .songSets(sets)
                .build();
    }

    // ── private helpers ──────────────────────────────────────────────

    /**
     * Extracts the concert date from the {@code <time datetime="...">} element in the document.
     *
     * @param document the source document
     * @return the concert date as a {@link LocalDateTime}; time is midnight when only a date was encoded
     * @throws SetlistParseException if the {@code <time datetime>} element is missing or the value is unparseable
     */
    private LocalDateTime parseDate(Document document) {
        Element timeEl = document.selectFirst("time[datetime]");
        if (timeEl == null) {
            throw new SetlistParseException("Missing <time datetime> element in document");
        }

        String raw = timeEl.attr("datetime");
        try {
            return raw.contains("T")
                    ? LocalDateTime.parse(raw)
                    : LocalDate.parse(raw).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new SetlistParseException("Unparseable datetime value: " + raw, e);
        }
    }

    /**
     * Parses all set blocks in the document into an ordered list of {@link SongSet} objects.
     *
     * @param document the source document
     * @return ordered list of {@link SongSet} objects in DOM order; never null, may be empty
     */
    private List<SongSet> parseSets(Document document) {
        Elements setBlocks = document.select("div.setlist-set");
        if (setBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<SongSet> result = new ArrayList<>();
        int ordinal = 1;
        for (Element setBlock : setBlocks) {
            result.add(parseSet(setBlock, ordinal++));
        }
        return result;
    }

    /**
     * Parses a single set block element into a {@link SongSet}.
     *
     * @param setBlock the set block element
     * @param ordinal  the 1-based position of this set in the concert
     * @return a populated {@link SongSet}
     */
    private SongSet parseSet(Element setBlock, int ordinal) {
        String label = extractLabel(setBlock);
        boolean encore = isEncore(label);
        List<Song> songs = parseSongs(setBlock);

        return SongSet.builder()
                .ordinal(ordinal)
                .songs(songs)
                .encore(encore)
                .build();
    }

    /**
     * Extracts the set label from the first heading element ({@code h2} or {@code h3}) within
     * the set block.
     *
     * @param setBlock the set block element
     * @return trimmed heading text, or an empty string when no heading element is present
     */
    private String extractLabel(Element setBlock) {
        Element heading = setBlock.selectFirst("h2, h3");
        return heading != null ? heading.text().trim() : "";
    }

    /**
     * Returns {@code true} when the label indicates an encore set (matches {@code ^Encore.*}
     * case-insensitively).
     *
     * @param label the set label string
     * @return {@code true} if the label marks this set as an encore
     */
    private boolean isEncore(String label) {
        return label.matches("(?i)^Encore.*");
    }

    /**
     * Parses all song entries within a set block into an ordered list of {@link Song} objects.
     *
     * @param setBlock the set block element
     * @return ordered list of {@link Song} objects in DOM order; never null, may be empty
     */
    private List<Song> parseSongs(Element setBlock) {
        Elements songEntries = setBlock.select("li.song");
        if (songEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Song> songs = new ArrayList<>();
        for (Element songEntry : songEntries) {
            songs.add(parseSong(songEntry));
        }
        return songs;
    }

    /**
     * Parses a single song entry element into a {@link Song}.
     *
     * <p>Extraction rules:
     * <ul>
     *   <li><b>title</b> — text of the first {@code <a>} element, trimmed; falls back to
     *       the element's own text with annotation and segue characters stripped.</li>
     *   <li><b>annotation</b> — parenthetical phrases ({@code (...) }) found in text nodes
     *       and {@code .songInfo} spans, concatenated in DOM order with a single space;
     *       {@code null} when none are present.</li>
     *   <li><b>segue</b> — {@code true} when a {@code .segue} element or a text node
     *       containing {@code >} is present within the entry.</li>
     *   <li><b>lyrics</b> — always {@code null}.</li>
     * </ul>
     *
     * @param songEntry the {@code <li class="song">} element
     * @return a populated {@link Song}
     */
    private Song parseSong(Element songEntry) {
        // ── Title ────────────────────────────────────────────────────────────
        String title;
        Element anchor = songEntry.selectFirst("a");
        if (anchor != null) {
            title = anchor.text().trim();
        } else {
            // Fallback: use the element's own text but strip annotation and segue artefacts.
            // Remove annotation spans and segue spans first, then take remaining text.
            Element clone = songEntry.clone();
            clone.select(".songInfo, .segue").remove();
            // Strip any residual parenthetical fragments and '>' from raw text
            String raw = clone.text();
            raw = raw.replaceAll("\\(.*?\\)", "").replace(">", "").trim();
            title = raw;
        }

        // ── Annotation ───────────────────────────────────────────────────────
        // Collect parenthetical phrases from .songInfo spans first, then from
        // text nodes in case the page uses inline text rather than spans.
        List<String> annotationPhrases = new ArrayList<>();

        Elements songInfoSpans = songEntry.select(".songInfo");
        if (!songInfoSpans.isEmpty()) {
            for (Element span : songInfoSpans) {
                String spanText = span.text().trim();
                // Strip surrounding parentheses if present
                if (spanText.startsWith("(") && spanText.endsWith(")")) {
                    spanText = spanText.substring(1, spanText.length() - 1).trim();
                }
                if (!spanText.isEmpty()) {
                    annotationPhrases.add(spanText);
                }
            }
        } else {
            // No .songInfo spans — scan text nodes for parenthetical content
            Pattern paren = Pattern.compile("\\(([^)]+)\\)");
            for (Node node : songEntry.childNodes()) {
                if (node instanceof TextNode) {
                    String text = ((TextNode) node).text();
                    Matcher m = paren.matcher(text);
                    while (m.find()) {
                        annotationPhrases.add(m.group(1).trim());
                    }
                }
            }
        }

        String annotation = annotationPhrases.isEmpty()
                ? null
                : String.join(" ", annotationPhrases);

        // ── Segue ─────────────────────────────────────────────────────────────
        boolean segue = false;
        Element segueEl = songEntry.selectFirst(".segue");
        if (segueEl != null) {
            // Dedicated segue element present
            segue = true;
        } else {
            // Check text nodes for a '>' character (may appear as a bare text node)
            for (Node node : songEntry.childNodes()) {
                if (node instanceof TextNode) {
                    if (((TextNode) node).text().contains(">")) {
                        segue = true;
                        break;
                    }
                }
            }
        }

        return Song.builder()
                .title(title)
                .lyrics(null)
                .annotation(annotation)
                .segue(segue)
                .build();
    }
}
