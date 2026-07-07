ALTER TABLE venues
    ADD COLUMN owner_id BIGINT;

ALTER TABLE venues
    ADD CONSTRAINT fk_venues_owner
        FOREIGN KEY (owner_id)
            REFERENCES users(id);