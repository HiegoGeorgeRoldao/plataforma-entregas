package br.com.hiego.entregas.kafka.producer;

import br.com.hiego.entregas.service.OutboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxService service;

    public OutboxPublisher(OutboxService service) {
        this.service = service;
    }

    /**
     * Aciona a varredura da Outbox apos o intervalo configurado em
     * {@code app.outbox.poll-delay-ms}. A tarefa so e criada quando a mensageria
     * e a publicacao da Outbox estao habilitadas.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:3000}")
    public void publish() {
        service.publishPending();
    }
}
