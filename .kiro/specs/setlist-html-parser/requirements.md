# Requirements Document

## Introduction

This feature adds an HTML parsing capability to the kfm-ai application that extracts concert setlist data from setlist.fm HTML pages. The parser accepts a `org.jsoup.nodes.Document` (produced by an external `html-parser` library) representing a setlist.fm page, extracts the concert date, all sets (Set I, Set II, encore, etc.), and the ordered songs within each set, then maps the extracted data into the existing `SetList → SongSet → Song` domain hierarchy. The `html-parser` library is responsible for fetching and converting raw HTML into a `Document`; the `SetlistParser` is solely responsible for domain extraction from the already-parsed DOM.

Song lyrics are out of scope for this feature. The `lyrics` field on the `Song` domain type will always be `null` after parsing; a separate feature phase will handle lyric sourcing from a different dataset.

## Glossary

- **SetlistParser**: The Spring component responsible for accepting a `org.jsoup.nodes.Document` input and producing a `SetList` object.
- **SetList**: The top-level domain type containing a concert date (`LocalDateTime`) and an ordered list of `SongSet` objects.
- **SongSet**: A domain type representing a single set within a concert (e.g., Set I, Set II, Encore). Contains an ordinal integer, an ordered list of `Song` objects, and a boolean `encore` flag.
- **Song**: A domain type representing a single song performance. Contains a title string, an optional lyrics string (always `null` from this parser — see Introduction), a nullable `annotation` string, and a boolean `segue` field.
- **Set Label**: The human-readable heading on the source HTML page that identifies a group of songs (e.g., "Set 1", "Set 2", "Encore", "Encore 1").
- **Set Block**: A structural HTML element on a setlist.fm page that groups a label heading and its associated list of song entries.
- **Song Entry**: A single HTML element within a set block that contains the title text of one performed song, optionally followed by a parenthetical annotation and/or a segue marker.
- **Ordinal**: An integer that encodes the position of a `SongSet` within the concert. Sets are numbered in DOM order starting from 1. Encores continue the same sequence without resetting.
- **Source Document**: A `org.jsoup.nodes.Document` representing a setlist.fm concert page, produced by the `html-parser` library and provided as input to the `SetlistParser`.
- **Concert Date**: The date (and optionally time) of the concert as encoded in the `datetime` attribute of the `<time>` element on the setlist.fm page, represented as a `LocalDateTime`.
- **Encore Set**: A `SongSet` whose set label matches the encore pattern (e.g., "Encore", "Encore 1"). Represented as a regular `SongSet` in the `songSets` list with `encore = true`. Encores are not stored separately; they share the same ordered list and ordinal sequence as non-encore sets.
- **Song Annotation**: A parenthetical performance-specific note displayed on setlist.fm beneath a song entry (e.g., "(First live performance since 2019)", "(with special guest)"). Stored in the `annotation` field of `Song`. This is specific to the performance instance, not a general property of the song title.
- **Segue**: An indicator that the music from one song continued directly into the next without stopping, represented by a `>` symbol on the setlist.fm page adjacent to the song entry. Stored as `segue = true` on the `Song` that flows into the next.

---

## Requirements

### Requirement 1: Parse Concert Date

**User Story:** As a developer, I want to parse the concert date from a setlist.fm `Document`, so that the resulting `SetList` is stamped with the correct event date.

#### Acceptance Criteria

1. WHEN a `org.jsoup.nodes.Document` representing a setlist.fm page and containing a `<time>` element with a `datetime` attribute is provided, THE `SetlistParser` SHALL extract the value of that `datetime` attribute and populate the `date` field of the returned `SetList`.
2. WHEN the `datetime` attribute value contains only a date (no time component, e.g., `2024-05-10`), THE `SetlistParser` SHALL set the time portion of the resulting `LocalDateTime` to midnight (`00:00:00`).
3. IF the `Document` does not contain a `<time>` element with a `datetime` attribute, THEN THE `SetlistParser` SHALL throw a `SetlistParseException` whose message contains the text "datetime" to identify the missing element.
4. IF the `datetime` attribute value is present but cannot be parsed into a valid date, THEN THE `SetlistParser` SHALL throw a `SetlistParseException` whose message contains the unparseable value.

---

### Requirement 2: Parse Sets and Songs in Order

**User Story:** As a developer, I want to parse all sets and their songs in order from a setlist.fm `Document`, so that the `SetList` accurately reflects the full concert structure.

#### Acceptance Criteria

1. WHEN a `org.jsoup.nodes.Document` representing a setlist.fm page is provided, THE `SetlistParser` SHALL produce one `SongSet` per distinct set block found in the document, in DOM (document) order.
2. WHEN a set block contains one or more song entries, THE `SetlistParser` SHALL populate the `songs` list of the corresponding `SongSet` with `Song` objects in DOM order.
3. WHEN a song entry is parsed, THE `SetlistParser` SHALL assign the `title` field of the resulting `Song` to the song name text with all leading and trailing whitespace removed.
4. WHEN a song entry is parsed, THE `SetlistParser` SHALL set the `lyrics` field of the resulting `Song` to `null`, as lyric sourcing is out of scope for this feature and will be addressed in a later phase.
5. WHEN a set block contains no song entries, THE `SetlistParser` SHALL produce a `SongSet` with an empty (non-null) `songs` list for that block.

---

### Requirement 3: Assign Set Ordinals

**User Story:** As a developer, I want each `SongSet` to have a meaningful ordinal, so that the position of each set within the concert can be determined programmatically.

#### Acceptance Criteria

1. THE `SetlistParser` SHALL assign ordinals to `SongSet` objects in DOM order, starting at 1 and incrementing by 1 for each subsequent set block, regardless of the set label text.
2. WHEN a set block is labeled as an encore (e.g., the label text matches `^Encore.*` case-insensitively), THE `SetlistParser` SHALL assign it the next sequential ordinal in the same unbroken 1-based sequence — the counter SHALL NOT reset for encores.
3. WHEN a set block is labeled as an encore, THE `SetlistParser` SHALL set the `encore` field of the resulting `SongSet` to `true`; all non-encore set blocks SHALL have `encore` set to `false`.
4. WHEN the HTML contains exactly one set block with no section heading element, THE `SetlistParser` SHALL assign that `SongSet` an ordinal of `1`.
5. WHEN the HTML contains only encore-labeled set blocks and no numbered sets, THE `SetlistParser` SHALL assign ordinals starting at 1 and incrementing in DOM order, and SHALL set `encore = true` on each such `SongSet`.

---

### Requirement 4: Handle Empty and Partial Sets

**User Story:** As a developer, I want the parser to handle edge cases like empty sets or missing sections gracefully, so that the application does not fail on malformed or unusual setlist pages.

#### Acceptance Criteria

1. WHEN a set block in the HTML contains no song entries, THE `SetlistParser` SHALL include a `SongSet` for that block with an empty (non-null) `songs` list and the correct positional ordinal, rather than omitting it.
2. WHEN the HTML contains no set blocks at all, THE `SetlistParser` SHALL return a `SetList` whose `date` field is populated from the page and whose `songSets` field is an empty (non-null) list.
3. IF the provided `Document` is `null`, THEN THE `SetlistParser` SHALL throw an `IllegalArgumentException` whose message contains the text "Document input".
4. IF a set block is present but its label cannot be classified as a numbered set or an encore, THE `SetlistParser` SHALL still parse it, assign the next sequential ordinal, set `encore = false`, and include it in the output.

---

### Requirement 5: Round-Trip Structural Integrity

**User Story:** As a developer, I want confidence that parsing equivalent `Document` inputs always produces equivalent `SetList` outputs, so that the parser behaves deterministically.

#### Acceptance Criteria

1. WHEN a setlist.fm `Document` is parsed, THE `SetlistParser` SHALL produce a `SetList` whose `songSets` list size equals the number of distinct set blocks present in the source document.
2. WHEN a setlist.fm `Document` is parsed, THE `SetlistParser` SHALL produce a `SetList` where the total count of `Song` objects across all `SongSet` objects equals the total number of song entries in the source document.
3. WHEN a setlist.fm `Document` is parsed, THE `SetlistParser` SHALL produce a `SetList` where the `ordinal` values across all `SongSet` objects form a contiguous sequence `[1, 2, …, N]` with no gaps or duplicates, where N equals the number of set blocks.
4. WHEN a setlist.fm `Document` is parsed, THE `SetlistParser` SHALL produce a `SetList` where exactly the `SongSet` objects whose set label matches the encore pattern have `encore = true`, and all others have `encore = false`.
5. WHEN a setlist.fm `Document` is parsed, THE `SetlistParser` SHALL produce a `SetList` where the `annotation` field of each `Song` is non-null if and only if a parenthetical annotation was present in the corresponding song entry in the source document.
6. WHEN a setlist.fm `Document` is parsed, THE `SetlistParser` SHALL produce a `SetList` where the `segue` field of each `Song` is `true` if and only if a segue marker (`>`) was present adjacent to the corresponding song entry in the source document.
7. IF the source document contains zero set blocks, THEN THE `SetlistParser` SHALL produce a `SetList` with an empty `songSets` list and a non-null `date` field.

---

### Requirement 6: Integration as a Spring Component

**User Story:** As a developer, I want the setlist parser to be a Spring-managed component, so that it can be injected into services and controllers using standard dependency injection.

#### Acceptance Criteria

1. THE `SetlistParser` SHALL be declared with `@Component` (or a `@Component`-derived stereotype annotation) and SHALL expose all dependencies via constructor parameters, enabling constructor injection throughout the application context.
2. WHEN the Spring application context starts with external dependencies (database, Ollama) absent or mocked, THE `SetlistParser` bean SHALL be created without error, confirming it does not perform network or database access during initialization.
3. THE `SetlistParser` SHALL hold no mutable instance state that is modified after construction, so that the singleton bean is safe for concurrent use by multiple threads.

---

### Requirement 10: Accept a Pre-Parsed Document as Input

**User Story:** As a developer, I want the `SetlistParser` to accept a `org.jsoup.nodes.Document` produced by the existing `html-parser` library, so that HTTP fetching and DOM construction are handled by that library and the `SetlistParser` focuses solely on domain extraction.

#### Acceptance Criteria

1. THE `SetlistParser` SHALL expose a public `parse(org.jsoup.nodes.Document document)` method (or equivalent) as its primary entry point, accepting a fully-parsed Jsoup `Document` rather than a raw HTML string or URL.
2. THE `SetlistParser` SHALL NOT perform any HTTP requests, URL resolution, or raw HTML string parsing internally — those responsibilities belong to the `html-parser` library.
3. THE `kfm-ai` project SHALL declare `kfm:html-parser:0.0.1-SNAPSHOT` as a compile-scope Maven dependency so that `org.jsoup.nodes.Document` is available on the classpath via that library's transitive Jsoup dependency. The artifact is installed to the local Maven repository (`~/.m2`) via `mvn install` from the `html-parser` project.
4. WHEN a `null` `Document` is passed to `SetlistParser.parse()`, THE `SetlistParser` SHALL throw an `IllegalArgumentException` whose message contains the text "Document input".

---

### Requirement 7: Represent Encore Sets with an Encore Flag

**User Story:** As a developer, I want encore sets to be identifiable programmatically without treating them as a separate list, so that the full concert structure remains a single ordered sequence while encore sets can still be distinguished from regular sets.

#### Acceptance Criteria

1. THE `SongSet` domain type SHALL contain a boolean field `encore` that is `true` when the set originated from an encore-labeled set block and `false` otherwise.
2. WHEN a set block whose label matches `^Encore.*` (case-insensitive) is parsed, THE `SetlistParser` SHALL set `encore = true` on the resulting `SongSet` and SHALL include that `SongSet` in the same `songSets` list as non-encore sets, maintaining DOM order.
3. WHEN a set block is not labeled as an encore, THE `SetlistParser` SHALL set `encore = false` on the resulting `SongSet`.
4. THE `SetList` domain type SHALL NOT contain a separate list or field for encore sets; all sets (regular and encore) SHALL be represented solely within the `songSets` list.
5. WHEN a concert HTML page contains both regular sets and encore sets, THE `SetlistParser` SHALL produce a `songSets` list in which encore `SongSet` objects appear after all non-encore `SongSet` objects, consistent with their DOM order on the page.

---

### Requirement 8: Extract Per-Song Performance Annotations

**User Story:** As a developer, I want performance-specific notes associated with individual songs to be captured in the domain model, so that contextual information displayed on setlist.fm is not lost during parsing.

#### Acceptance Criteria

1. THE `Song` domain type SHALL contain a nullable `String` field named `annotation` to hold performance-specific notes.
2. WHEN a song entry in the source HTML contains a parenthetical annotation (text enclosed in parentheses appearing as part of the song entry, e.g., `(First live performance since 2019)`), THE `SetlistParser` SHALL extract that text — excluding the surrounding parentheses — and assign it to the `annotation` field of the resulting `Song`.
3. WHEN a song entry contains no parenthetical annotation, THE `SetlistParser` SHALL set the `annotation` field of the resulting `Song` to `null`.
4. WHEN a song entry contains a parenthetical annotation, THE `SetlistParser` SHALL assign the `title` field using only the song name text, excluding the annotation text, with all leading and trailing whitespace removed from the title.
5. WHEN a song entry contains multiple parenthetical phrases, THE `SetlistParser` SHALL concatenate them in DOM order, separated by a single space, and assign the result to the `annotation` field.

---

### Requirement 9: Detect Segue Markers Between Songs

**User Story:** As a developer, I want to know when a song transitioned directly into the next song without stopping, so that the parsed setlist accurately reflects the musical flow of the concert.

#### Acceptance Criteria

1. THE `Song` domain type SHALL contain a boolean field named `segue` that is `true` when the corresponding song entry on the source HTML page is followed by a `>` segue marker, and `false` otherwise.
2. WHEN a song entry in the source HTML has an adjacent `>` segue marker (appearing as a suffix element or text node within or immediately after the song entry element), THE `SetlistParser` SHALL set `segue = true` on the resulting `Song`.
3. WHEN a song entry in the source HTML has no adjacent `>` segue marker, THE `SetlistParser` SHALL set `segue = false` on the resulting `Song`.
4. WHEN extracting the `title` field of a `Song`, THE `SetlistParser` SHALL exclude any `>` segue marker text from the title value, so that the title contains only the song name.
5. WHEN the last song entry in a set block has a segue marker, THE `SetlistParser` SHALL set `segue = true` on that `Song`; the absence of a following song within the same set SHALL NOT suppress the segue flag.
