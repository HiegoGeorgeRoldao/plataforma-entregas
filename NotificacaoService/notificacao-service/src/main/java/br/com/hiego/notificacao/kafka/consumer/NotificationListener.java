package br.com.hiego.notificacao.kafka.consumer;

import br.com.hiego.messaging.IntegrationEvent;
import br.com.hiego.notificacao.service.NotificationHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Adaptador Kafka para eventos de atualizacao de entrega, habilitado por {@code app.messaging.enabled}. */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class NotificationListener {
    private final NotificationHandler handler;
    private final ObjectMapper mapper;

    /**
     * @param handler processamento transacional do evento
     * @param mapper conversor do JSON para o contrato compartilhado
     */
    public NotificationListener(NotificationHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    /**
     * Converte o JSON recebido no topico configurado em {@link IntegrationEvent},
     * de {@code messaging-core}, e delega ao service. Falhas de conversao ou
     * processamento propagam ao tratador Kafka importado pela configuracao,
     * que decide entre retentativa e encaminhamento para a DLT.
     *
     * @param payload JSON do envelope de {@code EntregaAtualizada}
     */
    @KafkaListener(topics = "${app.messaging.topics.atualizada}", groupId = "notificacao-service-v1")
    public void receive(String payload) {
        handler.process(mapper.readValue(payload, IntegrationEvent.class));
    }
}
