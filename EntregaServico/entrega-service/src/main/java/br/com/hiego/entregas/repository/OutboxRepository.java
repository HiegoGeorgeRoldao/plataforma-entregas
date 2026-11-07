package br.com.hiego.entregas.repository;

import br.com.hiego.entregas.entity.OutboxEvent;
import br.com.hiego.entregas.dto.EventoEntregaResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    /**
     * Projeta os registros da Outbox de uma entrega, respeitando o usuario dono.
     *
     * @param entregaId identificador da entrega
     * @param usuario dono da entrega
     * @return eventos ordenados por instante e identificador
     */
    @Query(
        """
        select new br.com.hiego.entregas.dto.EventoEntregaResponse(
                o.id,
                o.eventType,
                o.status,
                o.attempts,
                o.createdAt,
                o.publishedAt
        )
            from OutboxEvent o, Entrega e
        where e.id = :entregaId
        and e.usuarioId = :usuario
        and o.aggregateId = e.id
        order by o.createdAt, o.id
        """
    )
    List<EventoEntregaResponse> eventos(@Param("entregaId") UUID entregaId, @Param("usuario") UUID usuario);
}
