package kfm.ai.ingest;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SetlistIndexCrawler}.
 */
@ExtendWith(MockitoExtension.class)
class SetlistIndexCrawlerTest {

    @Mock
    private HtmlParserClient htmlParserClient;

    private BatchIngestProperties properties;
    private SetlistIndexCrawler crawler;

    @BeforeEach
    void setUp() {
        properties = new BatchIngestProperties();
        properties.setIndexUrl("https://www.setlist.fm/setlists/grateful-dead.html");
        properties.setRequestDelayMs(100);
        crawler = new SetlistIndexCrawler(htmlParserClient, properties);
    }

    @Test
    void discoverSetlistUrls_extractsDetailUrls() {
        Document page = Jsoup.parse(
                "<html><body>" +
                "<a href=\"https://www.setlist.fm/setlist/grateful-dead/1977/barton-hall-3bd6a04c.html\">Show 1</a>" +
                "<a href=\"https://www.setlist.fm/setlist/grateful-dead/1977/winterland-1bd6a05c.html\">Show 2</a>" +
                "<a href=\"https://www.setlist.fm/about\">About</a>" +
                "</body></html>"
        );
        page.setBaseUri("https://www.setlist.fm/setlists/grateful-dead.html");

        when(htmlParserClient.fetch(anyString())).thenReturn(page);

        List<String> urls = crawler.discoverSetlistUrls();

        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("barton-hall"));
        assertTrue(urls.get(1).contains("winterland"));
    }

    @Test
    void discoverSetlistUrls_deduplicatesUrls() {
        Document page = Jsoup.parse(
                "<html><body>" +
                "<a href=\"https://www.setlist.fm/setlist/grateful-dead/1977/barton-hall-3bd6a04c.html\">Show 1</a>" +
                "<a href=\"https://www.setlist.fm/setlist/grateful-dead/1977/barton-hall-3bd6a04c.html\">Show 1 again</a>" +
                "</body></html>"
        );
        page.setBaseUri("https://www.setlist.fm/setlists/grateful-dead.html");

        when(htmlParserClient.fetch(anyString())).thenReturn(page);

        List<String> urls = crawler.discoverSetlistUrls();

        assertEquals(1, urls.size());
    }

    @Test
    void discoverSetlistUrls_followsPagination() {
        Document page1 = Jsoup.parse(
                "<html><body>" +
                "<a href=\"https://www.setlist.fm/setlist/grateful-dead/1977/barton-hall-3bd6a04c.html\">Show 1</a>" +
                "<a rel=\"next\" href=\"https://www.setlist.fm/setlists/grateful-dead.html?page=2\">Next</a>" +
                "</body></html>"
        );
        page1.setBaseUri("https://www.setlist.fm/setlists/grateful-dead.html");

        Document page2 = Jsoup.parse(
                "<html><body>" +
                "<a href=\"https://www.setlist.fm/setlist/grateful-dead/1973/winterland-1bd6a05c.html\">Show 2</a>" +
                "</body></html>"
        );
        page2.setBaseUri("https://www.setlist.fm/setlists/grateful-dead.html?page=2");

        when(htmlParserClient.fetch("https://www.setlist.fm/setlists/grateful-dead.html")).thenReturn(page1);
        when(htmlParserClient.fetch("https://www.setlist.fm/setlists/grateful-dead.html?page=2")).thenReturn(page2);

        List<String> urls = crawler.discoverSetlistUrls();

        assertEquals(2, urls.size());
        verify(htmlParserClient, times(2)).fetch(anyString());
    }

    @Test
    void discoverSetlistUrls_htmlFetchException_stopsGracefully() {
        when(htmlParserClient.fetch(anyString()))
                .thenThrow(new HtmlFetchException("Network error"));

        List<String> urls = crawler.discoverSetlistUrls();

        assertTrue(urls.isEmpty());
    }

    @Test
    void discoverSetlistUrls_noMatchingUrls_returnsEmptyList() {
        Document page = Jsoup.parse(
                "<html><body>" +
                "<a href=\"https://www.setlist.fm/about\">About</a>" +
                "<a href=\"https://www.setlist.fm/concerts\">Concerts</a>" +
                "</body></html>"
        );
        page.setBaseUri("https://www.setlist.fm/setlists/grateful-dead.html");

        when(htmlParserClient.fetch(anyString())).thenReturn(page);

        List<String> urls = crawler.discoverSetlistUrls();

        assertTrue(urls.isEmpty());
    }
}
