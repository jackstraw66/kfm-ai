package kfm.ai.ingest;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * Discovers all individual Grateful Dead setlist page URLs by crawling
 * the setlist.fm index pages starting from the configured base URL.
 *
 * <p>Follows pagination links to collect URLs from all index pages,
 * deduplicates by full URL string, and applies a configurable rate-limit
 * delay between consecutive HTTP fetches.</p>
 */
@Slf4j
@Component
public class SetlistIndexCrawler {

    private static final String SETLIST_FM_BASE = "https://www.setlist.fm";
    private static final String SETLIST_PATH_PREFIX = "/setlist/";

    /**
     * Matches individual setlist detail page URLs.
     * Pattern: /setlist/{artist}/{year}/{venue}-{hexId}.html
     * Examples:
     *   /setlist/grateful-dead/1977/barton-hall-cornell-university-ithaca-ny-1bd6a04c.html
     */
    private static final Pattern SETLIST_DETAIL_URL_PATTERN = Pattern.compile(
            "/setlist/[^/]+/\\d{4}/[^/]+-[0-9a-f]+\\.html"
    );

    private final HtmlParserClient htmlParserClient;
    private final BatchIngestProperties batchIngestProperties;

    public SetlistIndexCrawler(HtmlParserClient htmlParserClient,
                               BatchIngestProperties batchIngestProperties) {
        this.htmlParserClient = htmlParserClient;
        this.batchIngestProperties = batchIngestProperties;
    }

    /**
     * Fetches all index pages starting from the configured base URL,
     * follows pagination, and returns a deduplicated list of show URLs.
     *
     * @return deduplicated list of individual setlist page URLs
     */
    public List<String> discoverSetlistUrls() {
        LinkedHashSet<String> discoveredUrls = new LinkedHashSet<>();
        String currentPageUrl = batchIngestProperties.getIndexUrl();
        boolean isFirstPage = true;

        while (currentPageUrl != null) {
            if (!isFirstPage) {
                applyRateLimitDelay();
            }

            Document page;
            try {
                page = htmlParserClient.fetch(currentPageUrl);
            } catch (HtmlFetchException ex) {
                log.error("Failed to fetch index page: {} - {}", currentPageUrl, ex.getMessage());
                currentPageUrl = null;
                continue;
            }

            isFirstPage = false;
            extractSetlistUrls(page, discoveredUrls);
            currentPageUrl = findNextPageUrl(page);
        }

        return new ArrayList<>(discoveredUrls);
    }

    /**
     * Extracts individual setlist page URLs from the given index page document.
     */
    private void extractSetlistUrls(Document page, LinkedHashSet<String> urls) {
        String baseUrl = page.baseUri();
        Elements links = page.select("a[href]");
        for (Element link : links) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) {
                href = link.attr("href");
                href = resolveUrl(href, baseUrl);
            }
            if (isSetlistDetailUrl(href)) {
                urls.add(href);
            }
        }
    }

    /**
     * Returns true if the URL matches the pattern for an individual setlist detail page.
     */
    private boolean isSetlistDetailUrl(String url) {
        return SETLIST_DETAIL_URL_PATTERN.matcher(url).find();
    }

    /**
     * Looks for a pagination "next" link in the given document.
     *
     * @return the full URL of the next page, or null if there is no next page
     */
    private String findNextPageUrl(Document page) {
        String baseUrl = page.baseUri();

        // Look for rel="next" link first
        Element nextLink = page.selectFirst("a[rel=next]");
        if (nextLink != null) {
            String href = nextLink.attr("abs:href");
            if (href.isEmpty()) {
                href = resolveUrl(nextLink.attr("href"), baseUrl);
            }
            return href;
        }

        // Fallback: look for pagination elements with "Next" text or arrow
        Elements paginationLinks = page.select("ul.pagination a, nav.pagination a, div.pagination a");
        for (Element link : paginationLinks) {
            String text = link.text().trim().toLowerCase();
            if (text.contains("next") || text.equals("›") || text.equals("»")) {
                String href = link.attr("abs:href");
                if (href.isEmpty()) {
                    href = resolveUrl(link.attr("href"), baseUrl);
                }
                return href;
            }
        }

        return null;
    }

    /**
     * Resolves a potentially relative URL to a full absolute URL,
     * correctly handling relative paths like "../setlist/...".
     *
     * @param href    the href value from the anchor tag
     * @param baseUrl the URL of the page containing the link (used as resolution base)
     */
    private String resolveUrl(String href, String baseUrl) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        try {
            URI base = URI.create(baseUrl);
            return base.resolve(href).normalize().toString();
        } catch (IllegalArgumentException ex) {
            log.warn("Could not resolve URL: {} relative to {} - {}", href, baseUrl, ex.getMessage());
            return SETLIST_FM_BASE + (href.startsWith("/") ? href : "/" + href);
        }
    }

    /**
     * Applies the configured rate-limit delay between consecutive fetches.
     */
    private void applyRateLimitDelay() {
        try {
            Thread.sleep(batchIngestProperties.getRequestDelayMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Rate-limit delay interrupted");
        }
    }
}
