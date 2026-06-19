# Implementation Plan: Setlist Batch Ingest

## Overview

This plan implements a batch ingestion pipeline that crawls Grateful Dead setlists from setlist.fm, parses each show page using the existing `SetlistParser`, and persists the entity graph into MySQL. The implementation proceeds bottom-up: entity promotion → repository layer → supporting components → orchestration service → tests.

## Tasks

- [x] 1. Promote domain types to JPA entities
  - [x] 1.1 Convert `SetList` from POJO to JPA entity
    - Add `@Entity`, `@Table` with unique constraints on `date` and `source_url`
    - Add `@Id` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
    - Add `sourceUrl` field (`@Column(name = "source_url", nullable = false, length = 2048)`)
    - Add `@OneToMany(mappedBy = "setList", cascade = {PERSIST, MERGE}, orphanRemoval = true)` on `songSets`
    - Add `@OrderBy("ordinal")` on `songSets`
    - Initialize `songSets` to `new ArrayList<>()` with `@Builder.Default`
    - _Requirements: 3.1, 3.4, 7.1, 7.3_

  - [x] 1.2 Convert `SongSet` from POJO to JPA entity
    - Add `@Entity`, `@Table(name = "song_set")`
    - Add `@Id` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
    - Add `@ManyToOne(fetch = FetchType.LAZY)` with `@JoinColumn(name = "set_list_id", nullable = false)` back-reference to `SetList`
    - Add `@OneToMany(mappedBy = "songSet", cascade = {PERSIST, MERGE}, orphanRemoval = true)` on `songs`
    - Add `@OrderBy("ordinal")` on `songs`
    - Initialize `songs` to `new ArrayList<>()` with `@Builder.Default`
    - Preserve existing `ordinal` and `encore` fields
    - _Requirements: 3.2, 3.5, 3.7_

  - [x] 1.3 Convert `Song` from POJO to JPA entity
    - Add `@Entity`, `@Table(name = "song")`
    - Add `@Id` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
    - Add `@ManyToOne(fetch = FetchType.LAZY)` with `@JoinColumn(name = "song_set_id", nullable = false)` back-reference to `SongSet`
    - Add `ordinal` field (int) for 1-based position within parent `SongSet`
    - Add `@Column(nullable = false)` on `title`
    - Add `@Column(columnDefinition = "TEXT")` on `lyrics`
    - _Requirements: 3.3, 3.6_

  - [x] 1.4 Create `SetListRepository` interface
    - Extend `JpaRepository<SetList, Long>`
    - Add `boolean existsByDate(LocalDateTime date)`
    - Add `boolean existsBySourceUrl(String sourceUrl)`
    - _Requirements: 4.1, 7.3_

- [x] 2. Checkpoint - Verify entity compilation
  - Ensure all entity classes compile correctly and existing parser tests still pass, ask the user if questions arise.

- [x] 3. Implement configuration and supporting components
  - [x] 3.1 Create `BatchIngestProperties` configuration class
    - Create `@ConfigurationProperties(prefix = "batch.ingest")` class
    - Add `indexUrl` (String) field for the base index URL
    - Add `requestDelayMs` (int) field with default value 1000
    - Add `@Min(100)` and `@Max(60000)` validation annotations on `requestDelayMs`
    - Add `@Validated` annotation on the class
    - Register with `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`
    - _Requirements: 1.1, 8.1, 8.2, 8.3, 8.4_

  - [x] 3.2 Create `HtmlParserClient` component
    - Create `@Component` class wrapping the `kfm:html-parser` library
    - Implement `public Document fetch(String url)` method
    - Wrap network/HTTP/timeout errors in a custom `HtmlFetchException`
    - _Requirements: 2.1, 2.4_

  - [x] 3.3 Create `HtmlFetchException` and `IngestAlreadyRunningException`
    - `HtmlFetchException` extends `RuntimeException` — for fetch failures
    - `IngestAlreadyRunningException` extends `RuntimeException` — for concurrent run rejection
    - _Requirements: 2.4, 6.4_

  - [x] 3.4 Create `IngestSummary` record
    - `public record IngestSummary(int discovered, int ingested, int skipped, int failed)`
    - _Requirements: 6.1_

- [x] 4. Implement URL discovery
  - [x] 4.1 Create `SetlistIndexCrawler` component
    - Create `@Component` class with `HtmlParserClient` and `BatchIngestProperties` injected
    - Implement `public List<String> discoverSetlistUrls()` method
    - Fetch the base index page, extract individual setlist page links
    - Follow pagination links to collect URLs from all index pages
    - Deduplicate URLs by full URL string using a `LinkedHashSet`
    - Apply rate-limit delay (`requestDelayMs`) between consecutive fetches
    - On index page fetch failure: log ERROR with page URL and HTTP status code, continue with remaining pages
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 8.1, 8.5_

  - [x] 4.2 Write property test for URL deduplication (Property 1)
    - **Property 1: URL Deduplication Invariant**
    - Generate random lists of URL strings with duplicates
    - Verify output has no duplicates and all unique inputs are present
    - **Validates: Requirements 1.3**

- [x] 5. Implement batch ingest orchestration
  - [x] 5.1 Create `BatchIngestService`
    - Create `@Service` class with `SetlistIndexCrawler`, `SetlistParser`, `SetListRepository`, `HtmlParserClient`, `BatchIngestProperties` injected
    - Add `AtomicBoolean running` field for concurrency guard
    - Implement `public IngestSummary runFullIngest()` method:
      - Check/set concurrency guard; throw `IngestAlreadyRunningException` on conflict
      - Call `SetlistIndexCrawler.discoverSetlistUrls()` for URL discovery
      - Iterate over discovered URLs: fetch → parse → set sourceUrl → dedup check → persist
      - Apply rate-limit delay between show page fetches
      - Accumulate discovered/ingested/skipped/failed counts
      - Log INFO summary on completion
      - Reset concurrency guard in `finally` block
    - Handle all-index-pages-fail scenario: return summary with zeros
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 8.1, 8.5_

  - [x] 5.2 Implement per-show persistence with transaction isolation
    - Create a helper method (or separate `@Transactional(propagation = REQUIRES_NEW)` bean method) for persisting a single show
    - Before persist: set back-references (`SongSet.setList`, `Song.songSet`) and assign song ordinals (1-based)
    - Check `existsByDate()` and `existsBySourceUrl()` before persisting; skip and log INFO if either matches
    - Catch persistence exceptions, log ERROR, increment `failed` counter
    - _Requirements: 2.3, 3.6, 4.1, 4.2, 4.4, 5.1, 5.2, 7.2, 7.4_

  - [x] 5.3 Implement error handling and logging
    - Log fetch failures at ERROR level with URL and exception message
    - Log parse failures at ERROR level with source URL and exception message
    - Log skipped shows at INFO level with concert date and source URL
    - Log summary at INFO level on completion with all four counts
    - _Requirements: 1.4, 2.4, 2.5, 5.1, 5.3_

- [x] 6. Checkpoint - Verify compilation and wiring
  - Ensure all components compile, dependency injection is correct, and application context loads, ask the user if questions arise.

- [x] 7. Write property-based tests
  - [x] 7.1 Write property test for failure isolation (Property 2)
    - **Property 2: Failure Isolation**
    - Generate random sequences of success/fail outcomes for a batch of N items
    - Verify all N - K non-failing items are processed to completion
    - **Validates: Requirements 1.4, 2.4, 2.5, 5.1**

  - [x] 7.2 Write property test for source URL population (Property 3)
    - **Property 3: Source URL Population**
    - Generate random URL strings and random SetList objects
    - Verify `sourceUrl` field equals the input URL after processing
    - **Validates: Requirements 2.3, 7.2**

  - [x] 7.3 Write property test for song ordinal sequence (Property 4)
    - **Property 4: Song Ordinal Sequence**
    - Generate random song lists of size 0..20
    - Verify ordinals form contiguous 1..N sequence with no gaps or duplicates
    - **Validates: Requirements 3.6**

  - [x] 7.4 Write property test for natural-key deduplication (Property 5)
    - **Property 5: Natural-Key Deduplication**
    - Generate random SetLists with random "existing" date/URL sets
    - Verify shows with matching keys are skipped, new shows are persisted
    - **Validates: Requirements 4.1, 4.2, 4.4, 7.4**

  - [x] 7.5 Write property test for summary count consistency (Property 6)
    - **Property 6: Summary Count Consistency**
    - Generate random batches with mixed success/skip/fail outcomes
    - Verify `ingested + skipped + failed = processed` and no negatives
    - **Validates: Requirements 4.3, 5.3**

  - [x] 7.6 Write property test for delay validation range (Property 7)
    - **Property 7: Delay Validation Range**
    - Generate random integers across full int range
    - Verify in-range values (100–60000) are accepted, out-of-range rejected
    - **Validates: Requirements 8.3, 8.4**

- [x] 8. Write integration tests
  - [x] 8.1 Write integration tests for JPA entity persistence
    - Test cascade persist: save SetList with SongSets and Songs, verify all rows created
    - Test orphan removal: remove a SongSet from SetList, verify row deleted
    - Test unique constraint on `date`: persist two SetLists with same date, verify constraint violation
    - Test unique constraint on `sourceUrl`: persist two SetLists with same URL, verify constraint violation
    - Use Testcontainers MySQL via existing `TestcontainersConfiguration`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 7.3_

  - [x] 8.2 Write integration tests for batch orchestration
    - Test transaction isolation: persist multiple shows, make one fail, verify prior shows committed
    - Test concurrency guard: invoke `runFullIngest()` from two threads, verify one rejected
    - Test all-index-pages-fail scenario: verify zero-count summary returned
    - _Requirements: 5.2, 5.4, 6.4_

- [x] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Integration tests use the existing Testcontainers MySQL setup
- Test properties should override `batch.ingest.request-delay-ms=0` for fast execution
- The `SetlistParser` is an existing component — no changes needed to it
- The `kfm:html-parser` library dependency is already in `pom.xml`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "3.3", "3.4"] },
    { "id": 1, "tasks": ["1.4", "3.1", "3.2"] },
    { "id": 2, "tasks": ["4.1"] },
    { "id": 3, "tasks": ["4.2", "5.1"] },
    { "id": 4, "tasks": ["5.2", "5.3"] },
    { "id": 5, "tasks": ["7.1", "7.2", "7.3", "7.4", "7.5", "7.6"] },
    { "id": 6, "tasks": ["8.1", "8.2"] }
  ]
}
```
