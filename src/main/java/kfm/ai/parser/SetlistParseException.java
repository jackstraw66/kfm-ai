package kfm.ai.parser;

/**
 * Thrown by {@link SetlistParser} when a required element is missing from the
 * source {@code Document} or when a parsed value cannot be interpreted (e.g. an
 * unparseable {@code datetime} attribute).
 *
 * <p>This is an unchecked exception; callers are not required to declare or catch it,
 * but should handle it when robust error reporting is needed.</p>
 */
public class SetlistParseException extends RuntimeException {

    /**
     * Constructs a new {@code SetlistParseException} with the specified detail message.
     *
     * @param message a human-readable description of the parse failure
     */
    public SetlistParseException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code SetlistParseException} with the specified detail message
     * and the underlying cause.
     *
     * @param message a human-readable description of the parse failure
     * @param cause   the exception that triggered this failure (e.g. a
     *                {@link java.time.format.DateTimeParseException})
     */
    public SetlistParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
