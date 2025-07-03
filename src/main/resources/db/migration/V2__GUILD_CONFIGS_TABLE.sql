CREATE TABLE guild_configs
(
    guild_id     VARCHAR(255) NOT NULL,
    config_key   VARCHAR(255) NOT NULL,
    config_value VARCHAR(255) NOT NULL,
    CONSTRAINT pk_guild_configs PRIMARY KEY (guild_id, config_key)
);