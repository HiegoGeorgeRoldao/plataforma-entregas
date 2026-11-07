package br.com.hiego.entregas.dto;

import java.time.Instant;
import java.util.UUID;

public record EventoEntregaResponse(UUID eventId, String eventType, String status,
                                    int attempts, Instant createdAt, Instant publishedAt) {}
