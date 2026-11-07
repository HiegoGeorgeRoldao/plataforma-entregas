package br.com.hiego.entregas;

import br.com.hiego.entregas.dto.SolicitarEntregaRequest;
import br.com.hiego.entregas.service.EntregaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.messaging.enabled=false", "app.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntregaReadApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EntregaService service;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM processed_event"); jdbc.update("DELETE FROM outbox_event"); jdbc.update("DELETE FROM entrega");
    }
    @Test void listaFiltraPaginaEResumeSomenteDoUsuario() throws Exception {
        var own = service.solicitar(new SolicitarEntregaRequest("31160100", "31615250"));
        jdbc.update("UPDATE entrega SET status='ANALISADA' WHERE id=?", own.id());
        var other = service.solicitar(new SolicitarEntregaRequest("01001000", "20040002"));
        jdbc.update("UPDATE entrega SET usuario_id=? WHERE id=?", UUID.randomUUID(), other.id());
        mvc.perform(get("/entregas?busca=31160-100&status=ANALISADA&tamanho=5"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamanho").value(5))
            .andExpect(jsonPath("$.itens[0].id").value(own.id().toString()));
        mvc.perform(get("/entregas?pagina=1&tamanho=1")).andExpect(jsonPath("$.itens.length()").value(0));
        mvc.perform(get("/entregas/resumo")).andExpect(jsonPath("$.solicitacoes").value(1))
            .andExpect(jsonPath("$.analisadas").value(1)).andExpect(jsonPath("$.aguardandoAnalise").value(0));
    }
    @Test void buscaPorUuidNormalizadoETrataCuringasComoLiterais() throws Exception {
        var entrega = service.solicitar(new SolicitarEntregaRequest("31160100", "31615250"));
        mvc.perform(get("/entregas").param("busca", "#" + entrega.id().toString().toUpperCase(java.util.Locale.ROOT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.itens[0].id").value(entrega.id().toString()));
        mvc.perform(get("/entregas").param("busca", "%_"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }


    @Test void mostraEventosReaisComIsolamentoDeUsuario() throws Exception {
        var delivery = service.solicitar(new SolicitarEntregaRequest("31160100", "31615250"));
        mvc.perform(get("/entregas/{id}/eventos", delivery.id())).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").value("EntregaSolicitada"))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
        jdbc.update("UPDATE entrega SET usuario_id=? WHERE id=?", UUID.randomUUID(), delivery.id());
        mvc.perform(get("/entregas/{id}/eventos", delivery.id())).andExpect(status().isNotFound());
    }
    @Test void consultaCotaEValidaEntrada() throws Exception {
        mvc.perform(get("/entregas/cota")).andExpect(status().isOk())
            .andExpect(jsonPath("$.limite").value(20)).andExpect(jsonPath("$.habilitada").value(false));
        mvc.perform(get("/entregas?tamanho=999")).andExpect(status().isBadRequest());
        mvc.perform(get("/entregas?pagina=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/entregas?pagina=100001")).andExpect(status().isBadRequest());
        mvc.perform(get("/entregas").param("busca", "x".repeat(81))).andExpect(status().isBadRequest());
        mvc.perform(get("/entregas?status=DESCONHECIDO")).andExpect(status().isBadRequest());
    }
}
