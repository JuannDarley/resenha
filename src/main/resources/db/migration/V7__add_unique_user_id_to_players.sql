ALTER TABLE players
    ADD CONSTRAINT uk_players_user
        UNIQUE (user_id);