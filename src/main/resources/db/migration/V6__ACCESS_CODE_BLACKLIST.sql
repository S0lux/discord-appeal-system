CREATE TABLE access_code_blacklist_entity
(
    access_code VARCHAR(255) NOT NULL,
    created_by  VARCHAR(255),
    created_at  TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_accesscodeblacklistentity PRIMARY KEY (access_code)
);