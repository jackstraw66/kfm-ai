package kfm.ai.ingest;

import kfm.htmlparser.client.HtmlFetcherClient;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HtmlParserClient}.
 */
@ExtendWith(MockitoExtension.class)
class HtmlParserClientTest {

    @Mock
    private HtmlFetcherClient htmlFetcherClient;

    private HtmlParserClient client;

    @BeforeEach
    void setUp() {
        client = new HtmlParserClient(htmlFetcherClient);
    }

    @Test
    void fetch_success_returnsDocument() {
        Document expected = Jsoup.parse("<html><body>Hello</body></html>");
        when(htmlFetcherClient.fetch("http://example.com")).thenReturn(expected);

        Document result = client.fetch("http://example.com");

        assertSame(expected, result);
        verify(htmlFetcherClient).fetch("http://example.com");
    }

    @Test
    void fetch_libraryThrows_wrapsAsHtmlFetchException() {
        when(htmlFetcherClient.fetch("http://fail.com"))
                .thenThrow(new kfm.htmlparser.client.HtmlFetchException("timeout"));

        HtmlFetchException ex = assertThrows(
                HtmlFetchException.class,
                () -> client.fetch("http://fail.com")
        );

        assertTrue(ex.getMessage().contains("http://fail.com"));
        assertNotNull(ex.getCause());
    }
}
