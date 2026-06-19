-- V1: Create set_list, song_set, and song tables for the batch ingest feature.

CREATE TABLE set_list (
    id BIGINT NOT NULL AUTO_INCREMENT,
    date DATETIME(6) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_set_list_date UNIQUE (date),
    CONSTRAINT uk_set_list_source_url UNIQUE (source_url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE song_set (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ordinal INT NOT NULL DEFAULT 0,
    encore BOOLEAN NOT NULL DEFAULT FALSE,
    set_list_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_song_set_set_list FOREIGN KEY (set_list_id) REFERENCES set_list(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE song (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    lyrics TEXT,
    annotation VARCHAR(255),
    segue BOOLEAN NOT NULL DEFAULT FALSE,
    ordinal INT NOT NULL DEFAULT 0,
    song_set_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_song_song_set FOREIGN KEY (song_set_id) REFERENCES song_set(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
