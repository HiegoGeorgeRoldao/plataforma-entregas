--liquibase formatted sql

--changeset hiego:001-create-entrega
CREATE TABLE entrega (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    cep_origem VARCHAR(8) NOT NULL CHECK (cep_origem ~ '^[0-9]{8}$'),
    cep_destino VARCHAR(8) NOT NULL CHECK (cep_destino ~ '^[0-9]{8}$'),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_entrega_usuario_created ON entrega (usuario_id, created_at DESC);
--rollback DROP TABLE entrega;

--changeset hiego:002-create-outbox
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    topic VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_error TEXT
);
CREATE INDEX idx_outbox_pending ON outbox_event (next_attempt_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_outbox_processing ON outbox_event (locked_until)
    WHERE status = 'PROCESSING';
--rollback DROP TABLE outbox_event;
