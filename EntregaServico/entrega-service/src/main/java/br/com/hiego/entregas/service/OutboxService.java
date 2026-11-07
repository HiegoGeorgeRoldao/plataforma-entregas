package br.com.hiego.entregas.service;

import br.com.hiego.entregas.repository.OutboxDispatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxService {
    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private final OutboxDispatchRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OutboxService(OutboxDispatchRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    /**
     * Processa ate 20 eventos pendentes por rodada. Cada evento recebe uma reserva
     * temporaria no banco; apos a confirmacao do Kafka, e marcado como publicado.
     * Em caso de falha, agenda nova tentativa com espera progressiva. O token da
     * reserva impede que uma tentativa antiga altere um registro assumido por outra.
     * Como a confirmacao do Kafka e a gravacao no banco sao operacoes separadas,
     * consumidores devem tolerar eventual entrega duplicada.
     */
    public void publishPending() {
        for (int i = 0; i < 20; i++) {
            UUID token = UUID.randomUUID();
            var claimed = repository.claimNext(token);
            if (claimed.isEmpty()) return;
            var event = claimed.get();
            try {
                kafka.send(event.topic(), event.aggregateId().toString(), event.payload()).get(10, TimeUnit.SECONDS);
                repository.markPublished(event.id(), token);
                log.info("Outbox publicada eventId={}", event.id());
            } catch (Exception exception) {
                long delay = Math.min(60, 1L << Math.min(6, event.attempts()));
                repository.reschedule(event.id(), token, delay, exception.getClass().getSimpleName());
                log.warn("Outbox reagendada eventId={} delaySeconds={}", event.id(), delay);
                if (exception instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
}
