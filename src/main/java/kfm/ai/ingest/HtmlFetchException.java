package kfm.ai.ingest;

/**
 * Thrown by {@link HtmlParserClient} when a network error, HTTP error, or
 * timeout occurs while fetching a page via the html-parser library.
 *
 * <p>This is an unchecked exception; callers are not required to declare or catch it,
 * but should handle it when robust error isolation is needed (e.g. during batch ingestion).</p>
 */
public class HtmlFetchException extends RuntimeException {

    /**
     * Constructs a new {@code HtmlFetchException} with the specified detail message.
     *
     * @param message a human-readable description of the fetch failure
     */
    public HtmlFetchException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code HtmlFetchException} with the specified detail message
     * and the underlying cause.
     *
     * @param message a human-readable description of the fetch failure
     * @param cause   the exception that triggered this failure (e.g. a
     *                {@link java.net.SocketTimeoutException} or {@link java.io.IOException})
     */
    public HtmlFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
