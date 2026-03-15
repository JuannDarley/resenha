CREATE TABLE players (
    id      BIGINT          NOT NULL AUTO_INCREMENT,
    name    VARCHAR(100)    NOT NULL,
    age     BIGINT          NOT NULL,
    email   VARCHAR(100)    NOT NULL,

    street          VARCHAR(100)    NOT NULL,
    neighborhood    VARCHAR(100)    NOT NULL,
    zip_code        VARCHAR(8)      NOT NULL,
    number          VARCHAR(20),
    complement      VARCHAR(100),
    city            VARCHAR(100)    NOT NULL,
    state           VARCHAR(2)      NOT NULL,

    PRIMARY KEY (id)
);