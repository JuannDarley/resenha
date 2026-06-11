ALTER TABLE users
DROP CONSTRAINT fk_users_player;

ALTER TABLE users
DROP COLUMN player_id;