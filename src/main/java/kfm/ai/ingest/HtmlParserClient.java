package kfm.ai.ingest;

import kfm.htmlparser.client.HtmlFetcherClient;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Thin adapter around the {@code kfm:html-parser} library's {@link HtmlFetcherClient}.
 *
 * <p>This component provides a consistent exception type ({@link HtmlFetchException}) within
 * this codebase regardless of what the underlying library throws. All network, HTTP, and
 * timeout errors from the library are caught and re-thrown as our local exception.</p>
 */
@Component
public class HtmlParserClient {

    private final HtmlFetcherClient htmlFetcherClient;

    public HtmlParserClient(HtmlFetcherClient htmlFetcherClient) {
        this.htmlFetcherClient = htmlFetcherClient;
    }

    /**
     * Fetches the given URL via the html-parser library and returns a Jsoup Document.
     *
     * @param url the URL to fetch; must not be null or blank
     * @return the parsed {@link Document}
     * @throws HtmlFetchException on network error, HTTP error, or timeout
     */
    public Document fetch(String url) {
        try {
            return htmlFetcherClient.fetch(url);
        } catch (kfm.htmlparser.client.HtmlFetchException ex) {
            throw new HtmlFetchException("Failed to fetch URL: " + url, ex);
        }
    }
}
