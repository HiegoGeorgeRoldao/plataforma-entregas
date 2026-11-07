--liquibase formatted sql
--changeset hiego:003-outbox-lease
ALTER TABLE outbox_event ADD COLUMN lease_token UUID;
--rollback ALTER TABLE outbox_event DROP COLUMN lease_token;

--changeset hiego:004-processed-event
CREATE TABLE processed_event (
    consumer_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
--rollback DROP TABLE processed_event;
