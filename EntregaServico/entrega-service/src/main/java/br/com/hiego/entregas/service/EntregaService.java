package br.com.hiego.entregas.service;

import br.com.hiego.entregas.dto.EntregaResponse;
import br.com.hiego.entregas.dto.SolicitarEntregaRequest;
import br.com.hiego.entregas.entity.Entrega;
import br.com.hiego.entregas.entity.OutboxEvent;
import br.com.hiego.entregas.exception.EntregaNaoEncontradaException;
import br.com.hiego.entregas.repository.EntregaRepository;
import br.com.hiego.entregas.repository.OutboxRepository;
import br.com.hiego.messaging.IntegrationEvent;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

@Service
public class EntregaService {
    private final int schemaVersion;
    private final String outputTopic;
    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);
    private final EntregaRepository entregas;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final UUID usuarioLocalId;

    public EntregaService(EntregaRepository entregas, OutboxRepository outbox,
                          ObjectMapper objectMapper,
                          @Value("${app.usuario-local-id}") UUID usuarioLocalId,
                          @Value("${app.messaging.schema.published-version}") int schemaVersion,
                          @Value("${app.messaging.topics.solicitada}") String outputTopic) {
        this.schemaVersion = schemaVersion; this.outputTopic = outputTopic;
        this.entregas = entregas;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.usuarioLocalId = usuarioLocalId;
    }

    /**
     * Grava a entrega e o evento {@code EntregaSolicitada} na Outbox na mesma transacao.
     * {@link IntegrationEvent#create(String, int, UUID, Map)} cria o envelope definido
     * por {@code messaging-core}; o envio ao Kafka ocorre depois, pelo publicador da Outbox.
     *
     * @param request CEPs informados na solicitacao
     * @return entrega criada com status inicial
     */
    @Transactional
    public EntregaResponse solicitar(SolicitarEntregaRequest request) {
        Instant agora = Instant.now();
        Entrega entrega = entregas.save(Entrega.solicitar(usuarioLocalId,
            request.cepOrigem(), request.cepDestino(), agora));
        var event = IntegrationEvent.create("EntregaSolicitada", schemaVersion, entrega.getId(), Map.of(
            "usuarioId", usuarioLocalId.toString(), "cepOrigem", entrega.getCepOrigem(),
            "cepDestino", entrega.getCepDestino()));
        outbox.save(OutboxEvent.from(event, objectMapper.writeValueAsString(event), outputTopic));
        log.info("Entrega e evento preparados entregaId={} eventId={}", entrega.getId(), event.eventId());
        return EntregaResponse.from(entrega);
    }

    /**
     * Consulta uma entrega somente quando pertence ao usuario local configurado.
     *
     * @param id identificador da entrega
     * @return dados atuais da entrega
     * @throws EntregaNaoEncontradaException se a entrega nao existir ou pertencer a outro usuario
     */
    @Transactional(readOnly = true)
    public EntregaResponse consultar(UUID id) {
        return entregas.findByIdAndUsuarioId(id, usuarioLocalId)
            .map(EntregaResponse::from)
            .orElseThrow(EntregaNaoEncontradaException::new);
    }
}
