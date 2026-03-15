CREATE TABLE venues (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(255)    NOT NULL,
    opening_time    VARCHAR(5)      NOT NULL,
    closing_time    VARCHAR(5)      NOT NULL,
    court_count     INT             NOT NULL,

    street          VARCHAR(100)    NOT NULL,
    neighborhood    VARCHAR(100)    NOT NULL,
    zip_code        VARCHAR(8)      NOT NULL,
    number          VARCHAR(20),
    complement      VARCHAR(100),
    city            VARCHAR(100)    NOT NULL,
    state           VARCHAR(2)      NOT NULL,

    PRIMARY KEY (id)
);

CREATE TABLE courts (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)    NOT NULL,
    type        VARCHAR(20)     NOT NULL,
    venue_id    BIGINT          NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (venue_id) REFERENCES venues(id)
);