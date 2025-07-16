CREATE TABLE cases
(
    id                  UUID                        NOT NULL,
    game                VARCHAR(255)                NOT NULL,
    appealer_discord_id VARCHAR(255)                NOT NULL,
    appealer_roblox_id  VARCHAR(255)                NOT NULL,
    appeal_platform     VARCHAR(255)                NOT NULL,
    appeal_verdict      VARCHAR(255)                NOT NULL,
    appeal_reason       TEXT                        NOT NULL,
    punishment_type     VARCHAR(255)                NOT NULL,
    punishment_reason   TEXT                        NOT NULL,
    channel_id          VARCHAR(255)                NOT NULL,
    appealed_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    closed_at           TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_cases PRIMARY KEY (id)
);

CREATE TABLE guild_configs
(
    guild_id     VARCHAR(255) NOT NULL,
    config_key   VARCHAR(255) NOT NULL,
    config_value VARCHAR(255) NOT NULL,
    CONSTRAINT pk_guild_configs PRIMARY KEY (guild_id, config_key)
);