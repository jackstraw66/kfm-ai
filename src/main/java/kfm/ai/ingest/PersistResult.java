package kfm.ai.ingest;

/**
 * Result of attempting to persist a single show.
 */
public enum PersistResult {
    /** Show was successfully persisted. */
    INGESTED,
    /** Show was skipped because it already exists (duplicate date or sourceUrl). */
    SKIPPED,
    /** Show persistence failed due to an exception. */
    FAILED
}
