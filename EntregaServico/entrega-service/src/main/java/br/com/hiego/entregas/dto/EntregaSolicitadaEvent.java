package br.com.hiego.entregas.dto;

import java.time.Instant;
import java.util.UUID;

public record EntregaSolicitadaEvent(UUID eventId, String eventType, int schemaVersion,
                                    UUID aggregateId, Instant occurredAt, Dados data) {
    public record Dados(UUID usuarioId, String cepOrigem, String cepDestino) {}
}
