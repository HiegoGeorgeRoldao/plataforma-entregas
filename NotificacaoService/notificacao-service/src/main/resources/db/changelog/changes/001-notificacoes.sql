--liquibase formatted sql
--changeset hiego:notificacao-001
CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE TABLE notificacao (
    id UUID PRIMARY KEY,
    entrega_id UUID NOT NULL UNIQUE,
    usuario_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('ANALISADA','REJEITADA')),
    mensagem TEXT NOT NULL,
    lida BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_notificacao_usuario ON notificacao (usuario_id, created_at DESC);
--rollback DROP TABLE notificacao; DROP TABLE processed_event;
