package br.com.hiego.analise.service;

import br.com.hiego.analise.entity.MongoOutbox;
import br.com.hiego.analise.repository.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Coordena publicacao, confirmacao e reagendamento sem conhecer queries MongoDB. */
@Service
public class OutboxService {
    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final int BATCH_SIZE = 20;
    private static final long LEASE_SECONDS = 30;
    private static final long ACK_TIMEOUT_SECONDS = 10;
    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    /** Recebe o repositório da Outbox e o cliente Kafka usado para publicar os eventos. */
    public OutboxService(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    /** Reserva e publica ate vinte eventos, marcando sucesso somente apos confirmacao do Kafka. */
    public void publishPending() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            Instant now = Instant.now();
            String token = UUID.randomUUID().toString();
            var claimed = outbox.claimNext(now, now.plusSeconds(LEASE_SECONDS), token);
            if (claimed.isEmpty()) {
                return;
            }
            var event = claimed.get();
            try {
                kafka.send(event.topic(), event.aggregateId(), event.payload())
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (outbox.markPublished(event.id(), token, Instant.now())) {
                    log.info("Outbox Mongo publicada eventId={}", event.id());
                } else {
                    log.warn("Reserva da Outbox nao pertence mais ao publicador eventId={}", event.id());
                }

            } catch (Exception exception) {
                try {
                    reschedule(event, token, exception);
                } finally {

                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
    }

    /** Calcula o atraso da próxima tentativa e reagenda o evento se o token ainda for dono da reserva. */
    private void reschedule(MongoOutbox event, String token, Exception exception) {
        long delay = Math.min(60, 1L << Math.min(6, event.attempts()));

        if (outbox.reschedule(event.id(), token, Instant.now().plusSeconds(delay),
                exception.getClass().getSimpleName())) {
            log.warn("Outbox Mongo reagendada eventId={} delaySeconds={}", event.id(), delay);
        } else {
            log.warn("Reserva da Outbox alterada antes do reagendamento eventId={}", event.id());
        }

    }
}
