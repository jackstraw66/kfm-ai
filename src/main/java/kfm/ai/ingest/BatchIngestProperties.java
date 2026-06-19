package kfm.ai.ingest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the batch ingest pipeline.
 * <p>
 * Bound from {@code batch.ingest.*} in application.yaml.
 */
@ConfigurationProperties(prefix = "batch.ingest")
@Validated
@Data
public class BatchIngestProperties {

    /**
     * Base index URL to crawl for setlist page links (legacy, unused with API approach).
     */
    private String indexUrl;

    /**
     * setlist.fm API key for authentication.
     */
    private String apiKey;

    /**
     * Delay in milliseconds between consecutive HTTP requests.
     * Must be between 100 and 60000 (inclusive).
     */
    @Min(100)
    @Max(60000)
    private int requestDelayMs = 1000;
}
