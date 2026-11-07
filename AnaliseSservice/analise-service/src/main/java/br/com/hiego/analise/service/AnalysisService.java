package br.com.hiego.analise.service;

import br.com.hiego.analise.filds.EntregaEventFields;
import br.com.hiego.analise.enuns.AnalysisStatus;
import br.com.hiego.analise.enuns.ForecastStatus;
import br.com.hiego.analise.filds.AddressFields;
import br.com.hiego.analise.filds.ForecastFields;
import br.com.hiego.analise.filds.EntregaEventContract;
import br.com.hiego.analise.validation.CepValidator;
import br.com.hiego.analise.dto.AnalysisResult;
import br.com.hiego.analise.integration.AnalysisGateway;
import br.com.hiego.analise.repository.ProcessedEventRepository;
import br.com.hiego.messaging.IntegrationEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Valida a solicitacao, executa a analise e aplica as regras de aprovacao e fallback. */
@Service
public class AnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private final int schemaVersion;
    private final ProcessedEventRepository processedEvents;
    private final AnalysisGateway gateway;
    private final AnalysisWriter writer;

    /** Recebe as dependências de deduplicação, consulta externa e gravação, além da versão esperada do evento. */
    public AnalysisService(ProcessedEventRepository processedEvents, AnalysisGateway gateway,
                           AnalysisWriter writer,
                           @Value("${app.messaging.schema.published-version}") int schemaVersion) {
        this.schemaVersion = schemaVersion;
        this.processedEvents = processedEvents;
        this.gateway = gateway;
        this.writer = writer;
    }

    /** Executa chamadas externas fora da transacao e ignora ocorrencias ja processadas. */
    public void process(IntegrationEvent event) {
        event.requireVersion(schemaVersion);
        event.requireType(EntregaEventContract.ENTREGA_SOLICITADA);
        event.usuarioId();
        String origem = event.text(EntregaEventFields.CEP_ORIGEM);
        String destino = event.text(EntregaEventFields.CEP_DESTINO);
        if (!CepValidator.isValid(origem) || !CepValidator.isValid(destino)) {
            throw new IllegalArgumentException("CEP invalido no evento");
        }
        if (processedEvents.existsById(event.eventId().toString())) {
            return;
        }
        writer.persist(event, analyze(origem, destino));
    }

    /** Rejeita CEP inexistente; falha climatica permite concluir sem previsao. */
    public AnalysisResult analyze(String origem, String destino) {
        var from = gateway.findAddress(origem);
        var to = gateway.findAddress(destino);
        if (from.isEmpty() || to.isEmpty()) {
            return new AnalysisResult(AnalysisStatus.REJEITADA, "CEP de origem ou destino inexistente",
                from, to, Map.of(ForecastFields.STATUS, ForecastStatus.NAO_CONSULTADA.name()));
        }
        Map<String, Object> weather;
        try {
            weather = gateway.findForecast(to.get(AddressFields.CIDADE).toString(), to.get(AddressFields.UF).toString());
        } catch (Exception exception) {
            log.warn("CPTEC indisponivel; analise sera concluida sem previsao: {}",
                exception.getClass().getSimpleName());
            weather = Map.of(ForecastFields.FONTE, ForecastFields.FONTE_CPTEC, ForecastFields.STATUS, ForecastStatus.INDISPONIVEL.name());
        }
        String observation = ForecastStatus.DISPONIVEL.name().equals(weather.get(ForecastFields.STATUS))
            ? "Enderecos consultados; previsao disponivel para o destino"
            : "Enderecos consultados; previsao indisponivel para o destino";
        return new AnalysisResult(AnalysisStatus.ANALISADA, observation, from, to, weather);
    }
}
