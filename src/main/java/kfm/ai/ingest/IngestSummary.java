package kfm.ai.ingest;

/**
 * Immutable summary of a batch ingest run.
 *
 * @param discovered total unique URLs found during the discovery phase
 * @param ingested   shows successfully persisted
 * @param skipped    shows skipped due to duplicate date or sourceUrl
 * @param failed     shows that failed during fetch, parse, or persist
 */
public record IngestSummary(int discovered, int ingested, int skipped, int failed) {}
