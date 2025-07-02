CREATE TABLE cases
(
    id                  UUID         NOT NULL,
    appealer_discord_id VARCHAR(255) NOT NULL,
    appealer_roblox_id  VARCHAR(255) NOT NULL,
    appeal_type         VARCHAR(255) NOT NULL,
    appeal_verdict      VARCHAR(255) NOT NULL,
    appeal_reason       VARCHAR(255) NOT NULL,
    punishment_type     VARCHAR(255) NOT NULL,
    punishment_reason   VARCHAR(255) NOT NULL,
    appealed_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    closed_at           TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_cases PRIMARY KEY (id)
);