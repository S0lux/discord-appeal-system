ALTER TABLE cases
    ADD appeal_platform VARCHAR(255);

ALTER TABLE cases
    ALTER COLUMN appeal_platform SET NOT NULL;

ALTER TABLE cases
    DROP COLUMN appeal_type;