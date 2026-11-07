package br.com.hiego.entregas.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 80)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private int schemaVersion;

    @Column(nullable = false, length = 150)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private Instant lockedUntil;

    @Column(columnDefinition = "text")
    private String lastError;

    protected OutboxEvent() {}

    /**
     * Converte o envelope de {@code messaging-core} em um registro pendente da Outbox.
     * Preserva identificador, agregado, tipo, versao e instante do evento; o JSON
     * fornecido sera publicado posteriormente no topico informado.
     *
     * @param message envelope ja criado pela biblioteca compartilhada
     * @param payload representacao JSON do envelope
     * @param topic topico de destino da publicacao posterior
     * @return registro inicial com status {@code PENDING}
     */
    public static OutboxEvent from(br.com.hiego.messaging.IntegrationEvent message, String payload, String topic) {
        OutboxEvent event = new OutboxEvent();
        event.id = message.eventId();
        event.aggregateId = message.aggregateId();
        event.aggregateType = "Entrega";
        event.eventType = message.eventType();
        event.schemaVersion = message.schemaVersion();
        event.topic = topic;
        event.payload = payload;
        event.status = "PENDING";
        event.createdAt = message.occurredAt();
        event.nextAttemptAt = message.occurredAt();
        return event;
    }
}
