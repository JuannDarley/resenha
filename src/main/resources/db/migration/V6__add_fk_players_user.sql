ALTER TABLE players
    ADD CONSTRAINT fk_players_user
        FOREIGN KEY (user_id)
            REFERENCES users(id);