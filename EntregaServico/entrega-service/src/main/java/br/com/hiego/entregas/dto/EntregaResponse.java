package br.com.hiego.entregas.dto;

import br.com.hiego.entregas.entity.Entrega;
import br.com.hiego.entregas.enuns.StatusEntrega;
import java.time.Instant;
import java.util.UUID;

public record EntregaResponse(UUID id, String cepOrigem, String cepDestino,
                              StatusEntrega status, Instant createdAt) {
    /**
     * Extrai da entidade os dados expostos pela API de entregas.
     *
     * @param entrega entidade persistida
     * @return representacao publica da entrega
     */
    public static EntregaResponse from(Entrega entrega) {
        return new EntregaResponse(entrega.getId(), entrega.getCepOrigem(),
            entrega.getCepDestino(), entrega.getStatus(), entrega.getCreatedAt());
    }
}
