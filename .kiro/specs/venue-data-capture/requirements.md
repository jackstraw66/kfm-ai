# Requirements Document

## Introduction

This feature extends the existing setlist ingest pipeline to capture venue location data from the setlist.fm API. The `set_list` table will be augmented with three new nullable columns—`venue_name`, `city`, and `state`—populated during the batch ingest process from the nested venue/city data already present in the API response.

## Glossary

- **Ingest_Pipeline**: The batch process that fetches setlist data from the setlist.fm REST API and persists it to the local database.
- **SetList_Entity**: The JPA entity mapped to the `set_list` database table, representing a single concert.
- **ApiVenue_Record**: The Java record that deserializes venue data from the setlist.fm API response, including nested city information.
- **Flyway_Migration**: A versioned SQL script that applies schema changes to the MySQL database.
- **SetlistFm_API**: The external REST API at api.setlist.fm that provides setlist, venue, and city data for artists.

## Requirements

### Requirement 1: Database Schema Extension

**User Story:** As a developer, I want the `set_list` table to have `venue_name`, `city`, and `state` columns, so that venue location data can be stored alongside each show.

#### Acceptance Criteria

1. THE Flyway_Migration SHALL add a nullable VARCHAR(255) column named `venue_name` to the `set_list` table.
2. THE Flyway_Migration SHALL add a nullable VARCHAR(255) column named `city` to the `set_list` table.
3. THE Flyway_Migration SHALL add a nullable VARCHAR(100) column named `state` to the `set_list` table.
4. THE Flyway_Migration SHALL use the version number V2 to sequence after the existing V1 migration.
5. WHEN the Flyway_Migration is applied to a `set_list` table that already contains rows, THE Flyway_Migration SHALL preserve all existing data with the new columns set to NULL for those rows.

---

### Requirement 2: JPA Entity Update

**User Story:** As a developer, I want the SetList JPA entity to include venue fields, so that venue data is mapped between the application and the database.

#### Acceptance Criteria

1. THE SetList_Entity SHALL include a `venueName` field of type String mapped to the `venue_name` column with a maximum length of 255 characters.
2. THE SetList_Entity SHALL include a `city` field of type String mapped to the `city` column with a maximum length of 255 characters.
3. THE SetList_Entity SHALL include a `state` field of type String mapped to the `state` column with a maximum length of 255 characters.
4. THE SetList_Entity SHALL allow null values for `venueName`, `city`, and `state` fields by not applying a non-null constraint on their column mappings.
5. WHEN a SetList_Entity instance is persisted with non-null venue fields, THE SetList_Entity SHALL store and retrieve the `venueName`, `city`, and `state` values without data loss.

---

### Requirement 3: API Response Deserialization

**User Story:** As a developer, I want the API client to deserialize city and state data from the setlist.fm venue response, so that venue location information is available for persistence.

#### Acceptance Criteria

1. THE ApiVenue_Record SHALL include a `city` field that deserializes the nested city JSON object from the setlist.fm API response, where the city object contains at minimum a `name` field (string), a `state` field (string), and a `stateCode` field (string).
2. WHEN the setlist.fm API returns a venue with city data, THE ApiVenue_Record SHALL make the city name accessible via the city object's `name` field, the state name accessible via the city object's `state` field, and the state code accessible via the city object's `stateCode` field, each as a nullable string.
3. WHEN the setlist.fm API returns a venue where the city object is absent or null in the JSON response, THE ApiVenue_Record SHALL deserialize the `city` field as null without throwing an exception.
4. WHEN the setlist.fm API returns a venue with a city object that contains null or missing values for `state` or `stateCode`, THE ApiVenue_Record SHALL deserialize those individual fields as null while still providing any non-null city fields that are present.

---

### Requirement 4: Entity Mapping During Ingest

**User Story:** As a developer, I want the ingest process to populate venue fields on SetList entities from the API response, so that venue data is persisted with each show.

#### Acceptance Criteria

1. WHEN the SetlistFm_API response contains a venue with a non-blank name, THE Ingest_Pipeline SHALL set the `venueName` field on the SetList_Entity to that venue name, truncated to 512 characters if longer.
2. WHEN the SetlistFm_API response contains venue city data with a non-blank city name, THE Ingest_Pipeline SHALL set the `city` field on the SetList_Entity to that city name.
3. WHEN the SetlistFm_API response contains venue city data with a stateCode value, THE Ingest_Pipeline SHALL set the `state` field on the SetList_Entity to that stateCode value. IF no stateCode is present but a state name is present, THEN THE Ingest_Pipeline SHALL set the `state` field to the state name instead.
4. WHEN the SetlistFm_API response contains a venue without city data or with city data containing only blank values, THE Ingest_Pipeline SHALL set the `city` and `state` fields to null on the SetList_Entity.
5. WHEN the SetlistFm_API response contains no venue object or a venue with a blank or missing name, THE Ingest_Pipeline SHALL set the `venueName`, `city`, and `state` fields to null on the SetList_Entity.
6. IF the SetlistFm_API response contains a venue name, city, or state value that consists solely of whitespace, THEN THE Ingest_Pipeline SHALL treat that value as absent and apply the corresponding null-handling rule.

---

### Requirement 5: Backward Compatibility

**User Story:** As a developer, I want existing records without venue data to remain valid, so that the migration does not break existing functionality.

#### Acceptance Criteria

1. THE Flyway_Migration SHALL use only additive ALTER TABLE statements (adding nullable columns) and SHALL NOT drop, rename, or modify any existing columns in the `set_list` table.
2. WHEN the Flyway_Migration completes, THE `set_list` table SHALL contain the same number of rows with identical values in the `date`, `source_url`, and related child records as before the migration.
3. WHILE existing rows have null values in the `venue_name`, `city`, and `state` columns, THE SetList_Entity SHALL load those rows and return a fully populated object with null venue fields and no exception thrown.
4. IF the SetlistFm_API response contains no venue data for a show, THEN THE Ingest_Pipeline SHALL persist the SetList_Entity with null `venueName`, `city`, and `state` fields and return a PersistResult of INGESTED or SKIPPED.
