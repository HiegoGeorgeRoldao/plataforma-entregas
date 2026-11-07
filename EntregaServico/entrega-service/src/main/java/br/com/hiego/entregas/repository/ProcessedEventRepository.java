package br.com.hiego.entregas.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {
    private final JdbcTemplate jdbc;

    public ProcessedEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra o identificador de um evento consumido. A restricao unica do banco
     * faz com que entregas repetidas do Kafka nao sejam processadas novamente.
     * Quando chamado dentro da transacao de negocio, o registro e revertido junto
     * com a atualizacao da entrega se o processamento falhar.
     *
     * @param eventId identificador do envelope recebido
     * @return {@code true} se foi registrado agora; {@code false} se ja existia
     */
    public boolean registrar(UUID eventId) {
        return jdbc.update("INSERT INTO processed_event(consumer_name,event_id) " +
                                "VALUES ('entrega-service',?) " +
                                "ON CONFLICT DO NOTHING",
            eventId) > 0;
    }
}
