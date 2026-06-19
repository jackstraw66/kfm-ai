# Requirements Document

## Introduction

This feature adds a batch ingestion capability to the kfm-ai application that iterates over all Grateful Dead setlists from setlist.fm, fetches each show page, parses it using the existing `SetlistParser` component, and persists the resulting domain objects (`SetList`, `SongSet`, `Song`) into the MySQL database via Spring Data JPA.

The batch process is resilient: individual fetch or parse failures are logged and skipped without halting the overall run. The feature also supports incremental updates — shows already stored in the database are identified by their concert date and skipped on subsequent runs, allowing the batch to be re-executed safely to pick up newly added setlists.

The existing domain types (`SetList`, `SongSet`, `Song`) are currently plain Lombok POJOs. This feature will promote them to JPA entities with appropriate table mappings, relationships, and generated primary keys so that they can be persisted via Spring Data repositories.

## Glossary

- **Batch_Ingest_Service**: The Spring service component responsible for orchestrating the full batch run — discovering setlist URLs, fetching pages, invoking the parser, and persisting results.
- **Setlist_Index_Page**: A page on setlist.fm that lists links to individual concert setlist pages for the Grateful Dead. Multiple index pages may exist (paginated).
- **Setlist_Page**: A single setlist.fm page representing one Grateful Dead concert, containing the HTML structure that the `SetlistParser` can parse into a `SetList` domain object.
- **SetlistParser**: The existing Spring component (`kfm.ai.parser.SetlistParser`) that accepts a Jsoup `Document` and produces a `SetList` domain object.
- **SetList**: The top-level JPA entity representing a single concert. Contains a date (`LocalDateTime`), a source URL, and an ordered list of `SongSet` entities.
- **SongSet**: A JPA entity representing a single set within a concert. Contains an ordinal, a list of `Song` entities, and an `encore` flag. Owned by a `SetList` via a one-to-many relationship.
- **Song**: A JPA entity representing a single song performance. Contains a title, optional annotation, optional lyrics, a segue flag, and a positional ordinal within its parent `SongSet`. Owned by a `SongSet` via a one-to-many relationship.
- **Source_URL**: The full URL of the setlist.fm page from which a `SetList` was ingested. Stored on the `SetList` entity to enable provenance tracking and deduplication.
- **Ingest_Run**: A single execution of the batch ingestion process from start to finish, covering URL discovery through persistence.
- **Html_Parser_Library**: The external `kfm:html-parser:0.0.1-SNAPSHOT` dependency that handles HTTP fetching and Jsoup `Document` construction from a URL.

---

## Requirements

### Requirement 1: Discover All Grateful Dead Setlist URLs

**User Story:** As a developer, I want the batch process to discover all available Grateful Dead setlist page URLs from setlist.fm, so that every known show can be ingested.

#### Acceptance Criteria

1. WHEN an Ingest_Run is initiated, THE Batch_Ingest_Service SHALL navigate the Setlist_Index_Page(s) for the Grateful Dead on setlist.fm, starting from a configurable base index URL (e.g., `batch.ingest.index-url`), and collect all individual Setlist_Page URLs.
2. WHEN the Setlist_Index_Page contains pagination links, THE Batch_Ingest_Service SHALL follow all pagination links to collect URLs from every page of results.
3. THE Batch_Ingest_Service SHALL return a complete, deduplicated list of Setlist_Page URLs discovered during the index traversal, where deduplication is based on the full URL string.
4. IF a Setlist_Index_Page fails to load (including network errors, HTTP error responses, or a timeout exceeding 30 seconds) or parse, THEN THE Batch_Ingest_Service SHALL log the failure with the page URL and HTTP status code (if available) and continue processing any remaining index pages.

---

### Requirement 2: Fetch and Parse Individual Setlist Pages

**User Story:** As a developer, I want each discovered setlist URL to be fetched and parsed into the domain model, so that the concert data is available for persistence.

#### Acceptance Criteria

1. WHEN a Setlist_Page URL is processed, THE Batch_Ingest_Service SHALL use the Html_Parser_Library to fetch the page and produce a Jsoup `Document`.
2. WHEN a Jsoup `Document` is obtained, THE Batch_Ingest_Service SHALL invoke `SetlistParser.parse(Document)` to produce a `SetList` domain object.
3. WHEN `SetlistParser.parse(Document)` returns a `SetList` domain object, THE Batch_Ingest_Service SHALL set the `sourceUrl` field on that `SetList` to the Setlist_Page URL that was fetched, before any persistence operation is invoked.
4. IF the Html_Parser_Library fails to fetch or produce a `Document` for a given URL (including network errors, HTTP error responses, or a timeout exceeding 30 seconds), THEN THE Batch_Ingest_Service SHALL log the URL and the exception message at ERROR level, skip that URL, and continue processing the remaining URLs.
5. IF the `SetlistParser` throws any runtime exception (including `SetlistParseException` or `IllegalArgumentException`) for a given `Document`, THEN THE Batch_Ingest_Service SHALL log the Source_URL and the exception message at ERROR level, skip that show, and continue processing the remaining URLs.

---

### Requirement 3: Persist SetList, SongSet, and Song as JPA Entities

**User Story:** As a developer, I want the parsed concert data to be stored in the MySQL database as normalized relational entities, so that the data can be queried and analyzed.

#### Acceptance Criteria

1. THE `SetList` type SHALL be annotated as a JPA entity with a generated primary key and a unique constraint on the `date` field to prevent duplicate concert records.
2. THE `SongSet` type SHALL be annotated as a JPA entity with a generated primary key and a many-to-one relationship to its parent `SetList`.
3. THE `Song` type SHALL be annotated as a JPA entity with a generated primary key and a many-to-one relationship to its parent `SongSet`.
4. THE `SetList` entity SHALL cascade persist and merge operations to its child `SongSet` entities and SHALL remove orphaned `SongSet` rows when they are removed from the parent's collection.
5. THE `SongSet` entity SHALL cascade persist and merge operations to its child `Song` entities and SHALL remove orphaned `Song` rows when they are removed from the parent's collection.
6. THE `Song` entity SHALL include a positional ordinal field representing the song's 1-based position within its parent `SongSet`, where the first song in the set has ordinal value 1.
7. THE `SongSet` entity SHALL preserve its `ordinal` field value in the database to maintain set ordering within a concert.

---

### Requirement 4: Skip Already-Ingested Shows

**User Story:** As a developer, I want the batch process to skip shows that have already been persisted, so that re-running the ingest does not create duplicate records and subsequent runs complete faster.

#### Acceptance Criteria

1. WHEN a `SetList` is about to be persisted, THE Batch_Ingest_Service SHALL query the database to check whether a `SetList` with the same `date` value (full `LocalDateTime` precision) already exists.
2. IF a `SetList` with the same `date` value already exists in the database, THEN THE Batch_Ingest_Service SHALL skip persistence for that show and log a message at INFO level indicating the show was already ingested, including the concert date and Source_URL of the skipped show.
3. WHEN an Ingest_Run completes, THE Batch_Ingest_Service SHALL report the count of shows newly ingested and the count of shows skipped as already present in the summary result object.
4. THE deduplication check SHALL use the `date` field at full `LocalDateTime` precision as the natural key for identifying previously ingested shows, so that two concerts on the same calendar date but at different times are treated as distinct records.

---

### Requirement 5: Resilient Batch Execution

**User Story:** As a developer, I want the batch process to handle errors gracefully without aborting, so that a single bad page does not prevent the rest of the catalog from being ingested.

#### Acceptance Criteria

1. WHEN a fetch failure, parse failure, or persistence failure occurs for an individual show, THE Batch_Ingest_Service SHALL log the error details (URL, exception type, message) at ERROR level and continue processing the next URL.
2. THE Batch_Ingest_Service SHALL commit each successfully processed show independently, so that a failure on one show does not roll back previously persisted shows within the same Ingest_Run.
3. WHEN an Ingest_Run completes (whether all URLs succeed, some fail, or all fail), THE Batch_Ingest_Service SHALL log a summary at INFO level containing: total URLs discovered, shows successfully ingested, shows skipped (already present), and shows failed.
4. IF all Setlist_Index_Pages fail to load during URL discovery, THEN THE Batch_Ingest_Service SHALL log an ERROR indicating that no URLs were discovered, return a summary result object with zero discovered URLs and zero ingested shows, and terminate the run without throwing an unhandled exception.
5. WHEN an Ingest_Run completes after the all-index-pages-fail scenario, THE Batch_Ingest_Service SHALL still return the summary result object to the caller as defined in Requirement 6, with all count fields set to zero except the failed count which reflects the number of index page failures.

---

### Requirement 6: Expose Batch Ingest as a Triggerable Operation

**User Story:** As a developer, I want to trigger the batch ingest programmatically, so that it can be invoked from a controller endpoint, a CLI runner, or a scheduled task.

#### Acceptance Criteria

1. THE Batch_Ingest_Service SHALL expose a public method `runFullIngest()` that blocks the calling thread until the complete Ingest_Run finishes and returns a summary result object containing four numeric fields: `discovered`, `ingested`, `skipped`, and `failed`.
2. THE Batch_Ingest_Service SHALL be a Spring-managed bean injectable into controllers, command-line runners, or scheduled task configurations.
3. WHEN `runFullIngest()` is invoked, THE Batch_Ingest_Service SHALL execute the full pipeline synchronously: URL discovery, fetch, parse, deduplicate, persist, and summarize.
4. IF `runFullIngest()` is invoked while another Ingest_Run is already in progress, THEN THE Batch_Ingest_Service SHALL reject the call and return an indication that a run is already in progress, without starting a duplicate run.

---

### Requirement 7: Store Source URL on SetList Entity

**User Story:** As a developer, I want each persisted `SetList` to record the URL it was ingested from, so that I can trace data provenance and re-fetch if needed.

#### Acceptance Criteria

1. THE `SetList` entity SHALL contain a non-nullable `sourceUrl` string field with a maximum length of 2048 characters that stores the full setlist.fm URL from which the concert data was parsed.
2. WHEN a `SetList` is persisted, THE Batch_Ingest_Service SHALL populate the `sourceUrl` field with the Setlist_Page URL that was fetched.
3. THE `sourceUrl` field SHALL have a unique constraint in the database schema to prevent duplicate imports from the same page.
4. IF a `SetList` cannot be persisted because a record with the same `sourceUrl` already exists, THEN THE Batch_Ingest_Service SHALL skip that show, log a message indicating the duplicate source URL, and continue processing the remaining URLs.

---

### Requirement 8: Rate Limiting and Respectful Fetching

**User Story:** As a developer, I want the batch process to respect setlist.fm's server by spacing out requests, so that the application does not get rate-limited or banned.

#### Acceptance Criteria

1. WHEN fetching Setlist_Index_Pages or Setlist_Pages during an Ingest_Run, THE Batch_Ingest_Service SHALL introduce a configurable delay between consecutive HTTP requests to setlist.fm.
2. THE default delay between requests SHALL be 1000 milliseconds.
3. THE delay value SHALL be configurable via Spring application properties (e.g., `batch.ingest.request-delay-ms`) and SHALL accept values between 100 and 60000 milliseconds inclusive.
4. IF the configured delay value is less than 100 milliseconds or greater than 60000 milliseconds, THEN THE Batch_Ingest_Service SHALL fail to start and log an error message indicating the invalid delay value and the acceptable range.
5. WHILE the delay is active between requests, THE Batch_Ingest_Service SHALL NOT block other Spring-managed request-handling threads from serving concurrent HTTP requests to the application.
