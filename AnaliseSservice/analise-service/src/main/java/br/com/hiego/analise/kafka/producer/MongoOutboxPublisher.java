package br.com.hiego.analise.kafka.producer;

import br.com.hiego.analise.service.OutboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Adaptador de agendamento: dispara a publicacao sem acessar o banco diretamente. */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class MongoOutboxPublisher {

    private final OutboxService service;

    /** Recebe o serviço responsável pela publicação agendada da Outbox. */
    public MongoOutboxPublisher(OutboxService service) {
        this.service = service;
    }

    /** Executa o caso de uso no intervalo definido para a Outbox. */
    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:3000}")
    public void publish() {
        service.publishPending();
    }
}
