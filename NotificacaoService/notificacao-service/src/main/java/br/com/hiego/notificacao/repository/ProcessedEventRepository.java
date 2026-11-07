package br.com.hiego.notificacao.repository;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {
    private final JdbcClient jdbc;

    public ProcessedEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Registra o evento na transacao corrente e retorna {@code false} se ele ja foi registrado. */
    public boolean register(UUID eventId) {
        return jdbc.sql("""
                INSERT INTO processed_event(event_id)
                VALUES (:eventId)
                ON CONFLICT DO NOTHING
                """)
            .param("eventId", eventId)
            .update() > 0;
    }
}
