package br.com.hiego.entregas.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxDispatchRepository {
    private final JdbcTemplate jdbc;

    public OutboxDispatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reserva atomicamente o evento elegivel mais antigo usando
     * {@code FOR UPDATE SKIP LOCKED}. Tambem recupera eventos cuja reserva expirou.
     * Incrementa o numero de tentativas e associa o token a uma reserva de 30 segundos.
     *
     * @param token identificador exclusivo desta tentativa de publicacao
     * @return evento reservado ou vazio quando nao houver evento elegivel
     */
    public Optional<ClaimedEvent> claimNext(UUID token) {
        return jdbc.query("""
            UPDATE outbox_event 
            SET status='PROCESSING',
                lease_token=?,
                locked_until=now()+interval '30 seconds',
                attempts=attempts+1
            WHERE id=(
                        SELECT id 
                            FROM outbox_event
                        WHERE (
                                status='PENDING' 
                                AND next_attempt_at <= now()
                               )
                        OR (
                                status='PROCESSING' 
                                AND locked_until <= now()
                            )
            ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
            )
            RETURNING id, aggregate_id, topic, payload::text, attempts
            """,
            (rs, row) -> new ClaimedEvent(rs.getObject("id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                rs.getString("topic"), rs.getString("payload"), rs.getInt("attempts")), token).stream().findFirst();
    }

    /**
     * Marca o evento como publicado apenas se o token ainda for o da reserva atual.
     * Uma confirmacao tardia de outra tentativa nao altera o registro.
     *
     * @param id identificador do evento
     * @param token token recebido ao reservar o evento
     */
    public void markPublished(UUID id, UUID token) {
        jdbc.update("""
            UPDATE outbox_event 
            SET status='PUBLISHED',
                published_at=now(), 
                locked_until=NULL,
                lease_token=NULL, 
                last_error=NULL
            WHERE id=? 
              AND lease_token=?
            """,
                id,
                token
        );
    }

    /**
     * Libera a reserva atual e programa outra tentativa, guardando o tipo da falha.
     * A atualizacao so ocorre se o token ainda corresponder a esta tentativa.
     *
     * @param id identificador do evento
     * @param token token recebido ao reservar o evento
     * @param delay espera em segundos antes da nova tentativa
     * @param error nome da falha a registrar
     */
    public void reschedule(UUID id, UUID token, long delay, String error) {
        jdbc.update("""
            UPDATE outbox_event 
            SET status='PENDING', 
                locked_until=NULL, 
                lease_token=NULL,
                next_attempt_at=now()+(? * interval '1 second'), 
                last_error=?
            WHERE id=? 
              AND lease_token=?
            """,
                delay,
                error,
                id,
                token);
    }

    public record ClaimedEvent(UUID id, UUID aggregateId, String topic, String payload, int attempts) {}
}
