package br.com.hiego.entregas;

import br.com.hiego.entregas.dto.SolicitarEntregaRequest;
import br.com.hiego.entregas.entity.OutboxEvent;
import br.com.hiego.entregas.repository.OutboxRepository;
import br.com.hiego.entregas.service.EntregaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.messaging.enabled=false", "app.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntregaIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired EntregaService service;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean OutboxRepository outbox;

    @BeforeEach
    void limpar() {
        jdbc.update("DELETE FROM processed_event");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM entrega");
    }

    @Test
    void cadastraConsultaEGravaEventoPendente() throws Exception {
        var result = mvc.perform(post("/entregas").contentType("application/json")
            .content(""" 
                {"cepOrigem":"01001000","cepDestino":"20040002"}
                """))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("SOLICITADA"))
            .andReturn();
        String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/entregas/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cepDestino").value("20040002"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entrega", Long.class)).isEqualTo(1L);
        var event = jdbc.queryForMap("SELECT * FROM outbox_event");
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("aggregate_id").toString()).isEqualTo(id);
        assertThat(event.get("topic")).isEqualTo("entrega.solicitada.v1");
        var payload = mapper.readTree(event.get("payload").toString());
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.get("eventId").asText()).isEqualTo(event.get("id").toString());
        assertThat(payload.get("data").get("cepOrigem").asText()).isEqualTo("01001000");
    }

    @Test
    void rejeitaCepInvalidoSemPersistir() throws Exception {
        mvc.perform(post("/entregas").contentType("application/json")
            .content("""
                {"cepOrigem":"01001-000","cepDestino":"20040002"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("cepOrigem"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entrega", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long.class)).isZero();
    }

    @Test
    void falhaNaOutboxDesfazEntrega() {
        doAnswer(invocation -> {
            entityManager.flush(); // Garante INSERT real antes de testar rollback.
            throw new IllegalStateException("Falha simulada na Outbox");
        })
            .when(outbox).save(any(OutboxEvent.class));
        assertThatThrownBy(() -> service.solicitar(new SolicitarEntregaRequest("01001000", "20040002")))
            .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entrega", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long.class)).isZero();
    }

    @Test
    void retorna404ParaEntregaInexistente() throws Exception {
        mvc.perform(get("/entregas/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void naoConsultaEntregaDeOutroUsuario() throws Exception {
        var entrega = service.solicitar(new SolicitarEntregaRequest("01001000", "20040002"));
        jdbc.update("UPDATE entrega SET usuario_id = ? WHERE id = ?", UUID.randomUUID(), entrega.id());
        mvc.perform(get("/entregas/{id}", entrega.id())).andExpect(status().isNotFound());
    }

    @Test
    void rejeitaJsonEIdentificadorInvalidos() throws Exception {
        mvc.perform(post("/entregas").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/entregas/invalido")).andExpect(status().isBadRequest());
    }
}
