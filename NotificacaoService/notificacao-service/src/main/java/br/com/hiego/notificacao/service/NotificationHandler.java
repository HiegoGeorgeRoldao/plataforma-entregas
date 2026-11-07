package br.com.hiego.notificacao.service;

import br.com.hiego.messaging.IntegrationEvent;
import br.com.hiego.notificacao.enuns.NotificationStatus;
import br.com.hiego.notificacao.filds.EntregaEventContract;
import br.com.hiego.notificacao.filds.EntregaEventFields;
import br.com.hiego.notificacao.repository.NotificationRepository;
import br.com.hiego.notificacao.repository.ProcessedEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Valida eventos de entrega e coordena a criacao transacional das notificacoes. */
@Service
public class NotificationHandler {
    private final int schemaVersion;
    private final ProcessedEventRepository processedEvents;
    private final NotificationRepository notifications;

    /**
     * Recebe as dependencias de persistencia e a versao esperada do contrato.
     *
     * @param processedEvents registro dos eventos ja processados
     * @param notifications persistencia das notificacoes
     * @param schemaVersion valor de {@code app.messaging.schema.published-version}
     */
    public NotificationHandler(ProcessedEventRepository processedEvents,
                               NotificationRepository notifications,
                               @Value("${app.messaging.schema.published-version}") int schemaVersion) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.schemaVersion = schemaVersion;
    }

    /**
     * Processa {@code EntregaAtualizada} usando o envelope {@link IntegrationEvent}
     * de {@code messaging-core} para validar versao, tipo e campos obrigatorios.
     * Aceita somente estados finais e ignora eventos cujo identificador ja foi registrado.
     * O registro de deduplicacao e a notificacao participam da mesma transacao;
     * uma falha na persistencia reverte ambos. A restricao no banco permite uma
     * notificacao por entrega.
     *
     * @param event envelope recebido do Kafka
     * @throws IllegalArgumentException se a versao, o tipo, o estado ou os campos forem invalidos
     */
    @Transactional
    public void process(IntegrationEvent event) {
        event.requireVersion(schemaVersion);
        event.requireType(EntregaEventContract.ENTREGA_ATUALIZADA);
        NotificationStatus status = NotificationStatus.fromEvent(event.text(EntregaEventFields.STATUS));

        var usuarioId = event.usuarioId();
        String message = event.text(EntregaEventFields.OBSERVACAO);
        if (!processedEvents.register(event.eventId())) return;
        notifications.insert(event.aggregateId(), usuarioId, status, message);
    }
}
