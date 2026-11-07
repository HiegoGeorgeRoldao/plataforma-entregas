package br.com.hiego.analise;

import br.com.hiego.analise.enuns.AnalysisStatus;
import br.com.hiego.analise.controller.AnaliseController;
import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.service.AnaliseQueryService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnaliseControllerTests {
    private AnaliseQueryService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(AnaliseQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AnaliseController(service)).build();
    }

    @Test
    void consultaRetornaMesmoContratoJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findByEntregaId(id)).thenReturn(Optional.of(new Analise(id.toString(), "usuario",
            AnalysisStatus.ANALISADA, "Concluida", Map.of("cep", "01001000"), Map.of("cep", "20040002"),
            Map.of("status", "DISPONIVEL"), Instant.parse("2026-11-05T12:00:00Z"))));
        mvc.perform(get("/analises/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.usuarioId").value("usuario"))
            .andExpect(jsonPath("$.status").value("ANALISADA"))
            .andExpect(jsonPath("$.enderecoDestino.cep").value("20040002"))
            .andExpect(jsonPath("$.previsao.status").value("DISPONIVEL"));
        verify(service).findByEntregaId(id);
    }

    @Test
    void ausenciaRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findByEntregaId(id)).thenReturn(Optional.empty());
        mvc.perform(get("/analises/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void identificadorInvalidoRetorna400SemChamarService() throws Exception {
        mvc.perform(get("/analises/id-invalido")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
