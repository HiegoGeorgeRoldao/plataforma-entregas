package br.com.hiego.analise;

import br.com.hiego.analise.enuns.AnalysisStatus;
import br.com.hiego.analise.dto.AnalysisResult;
import br.com.hiego.analise.integration.AnalysisGateway;
import br.com.hiego.analise.repository.AnaliseRepository;
import br.com.hiego.analise.repository.ProcessedEventRepository;
import br.com.hiego.analise.service.AnaliseQueryService;
import br.com.hiego.analise.service.AnalysisService;
import br.com.hiego.analise.service.AnalysisWriter;
import br.com.hiego.messaging.IntegrationEvent;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisServiceTests {
    private ProcessedEventRepository processed;
    private AnalysisGateway gateway;
    private AnalysisWriter writer;
    private AnalysisService service;

    @BeforeEach
    void setup() {
        processed = mock(ProcessedEventRepository.class);
        gateway = mock(AnalysisGateway.class);
        writer = mock(AnalysisWriter.class);
        service = new AnalysisService(processed, gateway, writer, 1);
    }

    private IntegrationEvent event(String origem) {
        return IntegrationEvent.create("EntregaSolicitada", 1, UUID.randomUUID(), Map.of(
            "usuarioId", UUID.randomUUID().toString(), "cepOrigem", origem, "cepDestino", "20040002"));
    }

    @Test
    void eventoRepetidoNaoConsultaApisNemGravaNovamente() {
        var event = event("01001000");
        when(processed.existsById(event.eventId().toString())).thenReturn(true);
        service.process(event);
        verifyNoInteractions(gateway, writer);
    }

    @Test
    void cepInvalidoNaoAcessaPersistencia() {
        assertThatThrownBy(() -> service.process(event("123"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processed, gateway, writer);
    }

    @Test
    void cepInexistenteProduzRejeicaoSemConsultarClima() throws Exception {
        var event = event("00000000");
        when(gateway.findAddress("00000000")).thenReturn(Map.of());
        when(gateway.findAddress("20040002")).thenReturn(Map.of("cidade", "Rio de Janeiro", "uf", "RJ"));
        service.process(event);
        var result = ArgumentCaptor.forClass(AnalysisResult.class);
        verify(writer).persist(eq(event), result.capture());
        assertThat(result.getValue().status()).isEqualTo(AnalysisStatus.REJEITADA);
        assertThat(result.getValue().previsao()).containsEntry("status", "NAO_CONSULTADA");
        verify(gateway, never()).findForecast(anyString(), anyString());
    }

    @Test
    void consultaSempreIncluiUsuarioConfigurado() {
        var repository = mock(AnaliseRepository.class);
        var queryService = new AnaliseQueryService(repository, "usuario-local");
        UUID entregaId = UUID.randomUUID();
        queryService.findByEntregaId(entregaId);
        verify(repository).findByEntregaIdAndUsuarioId(entregaId.toString(), "usuario-local");
    }
}
