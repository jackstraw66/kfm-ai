-- V2: Add venue columns to set_list table for venue data capture.

ALTER TABLE set_list ADD COLUMN venue_name VARCHAR(512) NULL;
ALTER TABLE set_list ADD COLUMN city VARCHAR(255) NULL;
ALTER TABLE set_list ADD COLUMN state VARCHAR(100) NULL;
