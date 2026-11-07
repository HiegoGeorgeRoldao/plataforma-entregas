package br.com.hiego.notificacao.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(UUID id, UUID entregaId, String status, String mensagem,
                               boolean lida, Instant createdAt) {}
