package kfm.ai.ingest;

/**
 * Thrown when a concurrent invocation of {@code runFullIngest()} is rejected
 * because an ingest run is already in progress.
 *
 * <p>This is an unchecked exception signaling that the caller should retry later
 * or discard the duplicate request.</p>
 */
public class IngestAlreadyRunningException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "An ingest run is already in progress";

    /**
     * Constructs a new {@code IngestAlreadyRunningException} with a default message.
     */
    public IngestAlreadyRunningException() {
        super(DEFAULT_MESSAGE);
    }
}
