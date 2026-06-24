# Implementation Plan: Venue Data Capture

## Overview

This plan implements venue data capture by adding three nullable columns to the `set_list` table, updating the JPA entity, extending the API deserialization records, and wiring venue mapping into the existing ingest pipeline. Property-based tests validate correctness properties, and integration tests confirm end-to-end behavior with Testcontainers MySQL.

## Tasks

- [x] 1. Database migration and entity update
  - [x] 1.1 Create V2 Flyway migration to add venue columns
    - Create `src/main/resources/db/migration/V2__add_venue_columns.sql`
    - Add three ALTER TABLE statements: `venue_name VARCHAR(512) NULL`, `city VARCHAR(255) NULL`, `state VARCHAR(100) NULL`
    - Use only additive ALTER TABLE statements; no drops, renames, or modifications to existing columns
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 5.1, 5.2_

  - [x] 1.2 Add venue fields to the SetList JPA entity
    - Add `venueName` (String, mapped to `venue_name`, length 512), `city` (String, mapped to `city`), `state` (String, mapped to `state`, length 100) fields to `src/main/java/kfm/ai/types/SetList.java`
    - No `nullable = false` constraint on any of the new columns
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 5.3_

- [x] 2. API record extension and mapping logic
  - [x] 2.1 Add ApiCity record and extend ApiVenue with city field
    - Add `ApiCity` record with `name`, `state`, `stateCode` fields (annotated with `@JsonIgnoreProperties(ignoreUnknown = true)`) inside `SetlistFmApiClient`
    - Extend existing `ApiVenue` record to include an `ApiCity city` field
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 2.2 Implement venue mapping logic in mapToEntity()
    - After building the SetList entity in `mapToEntity()`, add venue field population logic
    - Map `venue.name` → `venueName` (truncated at 512 chars if longer, null if blank/whitespace)
    - Map `venue.city.name` → `city` (null if blank/whitespace or city absent)
    - Map `venue.city.stateCode` → `state` (prefer stateCode; fallback to state name; null if both blank)
    - Use `isBlank()` checks to treat whitespace-only values as absent
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.4_

- [x] 3. Checkpoint - Verify compilation and migration
  - Ensure all code compiles cleanly and the Flyway migration applies without error. Ask the user if questions arise.

- [x] 4. Property-based tests
  - [x] 4.1 Write property test for venue field persistence round-trip
    - **Property 1: Venue Field Persistence Round-Trip**
    - **Validates: Requirements 2.5**
    - Create `src/test/java/kfm/ai/ingest/VenueFieldPersistenceRoundTripPropertyTest.java`
    - Generate random non-blank strings within column length limits for venueName, city, state
    - Persist via `SetListRepository`, reload by ID, assert field equality
    - Use Testcontainers MySQL; minimum 100 tries

  - [x] 4.2 Write property test for API venue deserialization completeness
    - **Property 2: API Venue Deserialization Completeness**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
    - Create `src/test/java/kfm/ai/ingest/ApiVenueDeserializationPropertyTest.java`
    - Generate random JSON with varying city structures (present/absent/null fields)
    - Deserialize with Jackson ObjectMapper, verify field accessibility without exceptions
    - Minimum 100 tries

  - [x] 4.3 Write property test for venue field mapping correctness
    - **Property 3: Venue Field Mapping Correctness**
    - **Validates: Requirements 4.1, 4.2, 4.4, 4.5, 4.6**
    - Create `src/test/java/kfm/ai/ingest/VenueFieldMappingPropertyTest.java`
    - Generate random `ApiSetlist` objects with varying venue/city data (non-blank, blank, null, whitespace-only, long strings)
    - Call `mapToEntity()`, assert venueName and city populated correctly with truncation and null handling
    - Minimum 100 tries

  - [x] 4.4 Write property test for state field fallback logic
    - **Property 4: State Field Fallback Logic**
    - **Validates: Requirements 4.3, 4.6**
    - Create `src/test/java/kfm/ai/ingest/VenueStateFieldFallbackPropertyTest.java`
    - Generate `ApiCity` objects with varying state/stateCode combinations (both present, only stateCode, only state, both blank, whitespace-only)
    - Verify stateCode is preferred when non-blank, state name used as fallback, null when both absent
    - Minimum 100 tries

- [x] 5. Integration tests
  - [x] 5.1 Write integration test for migration and end-to-end ingest with venue data
    - Create `src/test/java/kfm/ai/ingest/VenueDataCaptureIntegrationTest.java`
    - Test that V2 migration applies to a database with existing rows and preserves data
    - Test end-to-end ingest with mock API returning venue data; verify persisted entity has correct venue fields
    - Test end-to-end ingest with mock API returning no venue data; verify null venue fields
    - Uses Testcontainers MySQL
    - _Requirements: 1.5, 2.5, 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.3, 5.4_

- [x] 6. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- The design specifies VARCHAR(512) for `venue_name` column to align truncation boundary with column width
- jqwik 1.9.3 and Testcontainers MySQL are already in pom.xml

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2"] },
    { "id": 3, "tasks": ["4.1", "4.2", "4.3", "4.4", "5.1"] }
  ]
}
```
