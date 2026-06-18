# Implementation Plan: setlist-html-parser

## Overview

Implement the `SetlistParser` Spring component that accepts a Jsoup `Document` (produced by the external `html-parser` library) and maps it into the `SetList → SongSet → Song` domain hierarchy. The implementation proceeds incrementally: update domain types first, add the Maven dependency, implement the parser with its exception, then add a full suite of unit and property-based tests.

## Tasks

- [x] 1. Add Maven dependency and update domain types
  - Add `kfm:html-parser:0.0.1-SNAPSHOT` compile-scope dependency to `pom.xml`
  - Add `jqwik:1.9.3` test-scope dependency to `pom.xml`
  - Add `encore` boolean field to `SongSet` (with Lombok `@Builder.Default = false`)
  - Add `annotation` nullable `String` field to `Song`
  - Add `segue` boolean field to `Song` (with Lombok `@Builder.Default = false`)
  - _Requirements: 6.1 (compile dep), 7.1 (encore), 8.1 (annotation), 9.1 (segue), 10.3_

- [x] 2. Create `SetlistParseException`
  - [x] 2.1 Implement `SetlistParseException` in `kfm.ai.parser`
    - Create `kfm/ai/parser/SetlistParseException.java` extending `RuntimeException`
    - Provide `(String message)` and `(String message, Throwable cause)` constructors
    - _Requirements: 1.3, 1.4_

- [x] 3. Implement `SetlistParser` — date parsing
  - [x] 3.1 Create `SetlistParser` skeleton and `parseDate` helper
    - Create `kfm/ai/parser/SetlistParser.java` with `@Component` annotation and no constructor parameters
    - Implement `public SetList parse(Document document)` entry point with null guard (throws `IllegalArgumentException` with "Document input")
    - Implement private `parseDate(Document document)`: select `time[datetime]`, throw `SetlistParseException` with "datetime" if missing, parse value via `LocalDate.parse().atStartOfDay()` or `LocalDateTime.parse()`, throw `SetlistParseException` with raw value on `DateTimeParseException`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 4.3, 6.1, 6.3, 10.1, 10.2, 10.4_

  - [x] 3.2 Write unit tests for `parseDate` behaviour
    - Test: `parse_null_document_throws` — null input → `IllegalArgumentException` message contains "Document input"
    - Test: `parse_missing_datetime_throws` — document with no `<time datetime>` → `SetlistParseException` message contains "datetime"
    - Test: `parse_date_only_midnight` — `datetime="2024-05-10"` → `LocalDateTime` with `T00:00:00`
    - Test: `parse_full_datetime` — `datetime="2024-05-10T20:30:00"` → exact `LocalDateTime`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 4.3, 10.4_

  - [x] 3.3 Write property test for date extraction round-trip (Property 1)
    - **Property 1: Date extraction round-trip**
    - **Validates: Requirements 1.1, 1.2**
    - Use jqwik `@Property` to generate arbitrary valid `yyyy-MM-dd` and ISO datetime strings, build synthetic Documents, assert parsed `date` encodes the same calendar date with midnight time when no time component was present
    - Tag comment: `// Feature: setlist-html-parser, Property 1: Date extraction round-trip`

  - [x] 3.4 Write property test for invalid datetime exception (Property 2)
    - **Property 2: Invalid datetime strings produce SetlistParseException with value in message**
    - **Validates: Requirements 1.4**
    - Use jqwik `@Property` to generate arbitrary non-date strings; build synthetic Documents with those strings as `datetime` attribute; assert `SetlistParseException` is thrown and its message contains the exact raw string
    - Tag comment: `// Feature: setlist-html-parser, Property 2: Invalid datetime strings produce SetlistParseException`

- [x] 4. Implement `SetlistParser` — set and song parsing
  - [x] 4.1 Implement `parseSets`, `parseSet`, and ordinal/encore assignment
    - Implement private `parseSets(Document document)`: select all set block elements in DOM order
    - Implement private `parseSet(Element setBlock, int ordinal)`: extract label via `extractLabel`, determine `encore` via `isEncore`, build `SongSet` with ordinal, songs, encore flag
    - Implement `extractLabel(Element setBlock)`: select first heading element (`h2`, `h3`) within the block; return empty string if absent
    - Implement `isEncore(String label)`: return `label.matches("(?i)^Encore.*")`
    - Wire `parseSets` into `parse()`: iterate blocks with a 1-based counter, collect `SongSet` list; if no blocks return empty non-null list
    - _Requirements: 2.1, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.4, 7.2, 7.3, 7.4, 7.5_

  - [x] 4.2 Write unit tests for set parsing and ordinals
    - Test: `parse_empty_set_block` — set block with no songs → `SongSet` with empty non-null `songs`, correct ordinal
    - Test: `parse_no_set_blocks` — document with date but no set blocks → empty non-null `songSets`
    - Test: `parse_unrecognised_label` — set block with unexpected label → `encore=false`, ordinal assigned sequentially
    - Test: `parse_encore_flag_set` — set block labeled "Encore" → `encore=true`
    - Test: `parse_encore_ordinal_continues` — two regular sets then one encore → ordinals [1, 2, 3]
    - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.4_

  - [x] 4.3 Write property test for set count invariant (Property 3)
    - **Property 3: Set count invariant**
    - **Validates: Requirements 2.1, 5.1, 4.2**
    - Use jqwik `@Property` to generate arbitrary N ≥ 0 set blocks in synthetic Documents; assert `songSets.size() == N`
    - Tag comment: `// Feature: setlist-html-parser, Property 3: Set count invariant`

  - [x] 4.4 Write property test for ordinal sequence (Property 5)
    - **Property 5: Ordinal sequence is contiguous and gapless**
    - **Validates: Requirements 3.1, 3.2, 5.3**
    - Use jqwik `@Property` to generate arbitrary N set blocks with mixed encore/regular labels; assert ordinal values form exactly `[1, 2, …, N]` in DOM order with no gaps, duplicates, or resets
    - Tag comment: `// Feature: setlist-html-parser, Property 5: Ordinal sequence is contiguous and gapless`

  - [x] 4.5 Write property test for encore flag (Property 6)
    - **Property 6: Encore flag matches label pattern**
    - **Validates: Requirements 3.3, 3.5, 5.4, 7.2, 7.3**
    - Use jqwik `@Property` to generate arbitrary mixes of encore and regular label strings; assert each `SongSet.encore` is `true` iff its label matches `^Encore.*` case-insensitively
    - Tag comment: `// Feature: setlist-html-parser, Property 6: Encore flag matches label pattern`

- [x] 5. Implement `SetlistParser` — song text extraction
  - [x] 5.1 Implement `parseSongs` and `parseSong` with title, annotation, segue
    - Implement `parseSongs(Element setBlock)`: select song entry elements in DOM order, map each through `parseSong`, return list (empty non-null when no entries)
    - Implement `parseSong(Element songEntry)`:
      - Extract `title` from `<a>` element text, trimmed; if no `<a>`, fall back to text content excluding annotations and segue
      - Collect all `(...)` parenthetical text fragments from remaining content (text nodes and annotation spans); concatenate in DOM order separated by `" "`; set `annotation` to result or `null` if none
      - Detect segue marker: check for `>` in dedicated segue element or adjacent text node; set `segue = true/false`
      - Explicitly exclude annotation text and `>` characters from the `title` value
      - Always set `lyrics = null`
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 8.2, 8.3, 8.4, 8.5, 9.2, 9.3, 9.4, 9.5_

  - [x] 5.2 Write unit tests for song text extraction
    - Test: `parse_null_lyrics` — any parsed song → `lyrics == null`
    - Test: `parse_song_title_trimmed` — song anchor with surrounding whitespace → `title` is trimmed
    - Test: `parse_song_no_annotation` — song with no parenthetical → `annotation == null`
    - Test: `parse_song_single_annotation` — song with one `(...)` phrase → `annotation` equals text without parens
    - Test: `parse_multiple_annotations` — song with two parenthetical phrases → joined by single space
    - Test: `parse_segue_marker_present` — song entry with `>` marker → `segue == true`
    - Test: `parse_segue_marker_absent` — song entry without `>` marker → `segue == false`
    - Test: `parse_last_song_segue` — last song in set with `>` marker → `segue == true`
    - Test: `parse_title_excludes_annotation` — title must not contain parenthetical text
    - Test: `parse_title_excludes_segue_char` — title must not contain `>`
    - _Requirements: 2.3, 2.4, 8.2, 8.3, 8.4, 8.5, 9.2, 9.3, 9.4, 9.5_

  - [x] 5.3 Write property test for song count invariant (Property 4)
    - **Property 4: Song count invariant**
    - **Validates: Requirements 2.2, 5.2**
    - Use jqwik `@Property` to generate arbitrary N sets with M_i songs each; assert total `Song` count across all `SongSet` objects equals sum of M_i
    - Tag comment: `// Feature: setlist-html-parser, Property 4: Song count invariant`

  - [x] 5.4 Write property test for title cleanliness (Property 7)
    - **Property 7: Song title is trimmed and excludes annotation and segue text**
    - **Validates: Requirements 2.3, 8.4, 9.4**
    - Use jqwik `@Property` to generate arbitrary song title strings with/without surrounding whitespace, optional annotation fragments, and optional `>` markers; assert `title` equals trimmed song name with no annotation or `>` content
    - Tag comment: `// Feature: setlist-html-parser, Property 7: Song title is trimmed and excludes annotation and segue text`

  - [x] 5.5 Write property test for annotation extraction (Property 8)
    - **Property 8: Annotation field matches source parenthetical text**
    - **Validates: Requirements 5.5, 8.2, 8.3, 8.5**
    - Use jqwik `@Property` to generate arbitrary annotation presence/absence and multi-phrase scenarios; assert `annotation` is non-null iff at least one parenthetical was present, and equals concatenated phrases joined by `" "` in DOM order
    - Tag comment: `// Feature: setlist-html-parser, Property 8: Annotation field matches source parenthetical text`

  - [x] 5.6 Write property test for segue flag (Property 9)
    - **Property 9: Segue flag matches source marker**
    - **Validates: Requirements 5.6, 9.2, 9.3, 9.5**
    - Use jqwik `@Property` to generate arbitrary songs with/without `>` markers (including last song in set); assert `segue` is `true` iff a `>` marker was present
    - Tag comment: `// Feature: setlist-html-parser, Property 9: Segue flag matches source marker`

- [x] 6. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Integration test: Spring context loads with `SetlistParser` bean
  - [x] 7.1 Write Spring context smoke test
    - Create a `@SpringBootTest(webEnvironment = NONE)` test that verifies the `SetlistParser` bean is created without error when DB and Ollama are absent or mocked
    - Assert `SetlistParser` can be `@Autowired` successfully
    - _Requirements: 6.1, 6.2_

- [x] 8. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties defined in the design (Properties 1–9)
- Unit tests validate specific examples and edge cases
- `SetlistParser` has no constructor parameters — do not introduce collaborators unnecessarily
- CSS selectors for setlist.fm must be validated against live pages during implementation; adjust selectors in `SetlistParser` as needed based on actual DOM inspection

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["2.1"] },
    { "id": 1, "tasks": ["3.1"] },
    { "id": 2, "tasks": ["3.2", "3.3", "3.4", "4.1"] },
    { "id": 3, "tasks": ["4.2", "4.3", "4.4", "4.5", "5.1"] },
    { "id": 4, "tasks": ["5.2", "5.3", "5.4", "5.5", "5.6"] },
    { "id": 5, "tasks": ["7.1"] }
  ]
}
```
