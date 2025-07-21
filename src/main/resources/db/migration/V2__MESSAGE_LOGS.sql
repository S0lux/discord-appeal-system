CREATE TABLE message_logs
(
    id                    VARCHAR(255)                NOT NULL,
    case_id               UUID                        NOT NULL,
    author_id             VARCHAR(255)                NOT NULL,
    content               TEXT                        NOT NULL,
    creation_timestamp    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_edited_timestamp TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_message_logs PRIMARY KEY (id)
);

ALTER TABLE cases
    ADD verdict_reason TEXT;

ALTER TABLE message_logs
    ADD CONSTRAINT FK_MESSAGE_LOGS_ON_CASE FOREIGN KEY (case_id) REFERENCES cases (id);