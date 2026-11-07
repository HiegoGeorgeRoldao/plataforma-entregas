package br.com.hiego.analise;

import br.com.hiego.analise.enuns.AnalysisStatus;
import br.com.hiego.analise.integration.*;
import br.com.hiego.analise.repository.ApiCacheRepository;
import br.com.hiego.analise.repository.ProcessedEventRepository;
import br.com.hiego.analise.service.AnalysisService;
import br.com.hiego.analise.service.AnalysisWriter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisGatewayTests {
    private HttpServer server;
    private AnalysisService service;
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var cache = mock(ApiCacheRepository.class);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var gateway = new AnalysisGateway(cache, JsonMapper.builder().build(), base, base + "/XML");
        service = new AnalysisService(mock(ProcessedEventRepository.class), gateway, mock(AnalysisWriter.class), 1);
        response("/ws/01001000/json/", 200, "{\"localidade\":\"Sao Paulo\",\"uf\":\"SP\"}");
        response("/ws/20040002/json/", 200, "{\"localidade\":\"Rio de Janeiro\",\"uf\":\"RJ\"}");
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private void response(String path, int code, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
    }
    @Test void converteEnderecoEPrevisaoXml() {
        response("/XML/listaCidades", 200, "<cidades><cidade><nome>Rio de Janeiro</nome><uf>RJ</uf><id>241</id></cidade></cidades>");
        response("/XML/cidade/241/previsao.xml", 200,
            "<cidade><previsao><dia>2026-11-04</dia><tempo>c</tempo><minima>20</minima><maxima>29</maxima></previsao></cidade>");
        var result = service.analyze("01001000", "20040002");
        assertThat(result.status()).isEqualTo(AnalysisStatus.ANALISADA);
        assertThat(result.previsao().get("status")).isEqualTo("DISPONIVEL");
        assertThat(result.destino().get("cidade")).isEqualTo("Rio de Janeiro");
    }
    @Test void rejeitaCepInexistente() {
        response("/ws/00000000/json/", 200, "{\"erro\":true}");
        assertThat(service.analyze("00000000", "20040002").status()).isEqualTo(AnalysisStatus.REJEITADA);
    }
    @Test void falhaClimaticaTemFallbackExplicito() {
        response("/XML/listaCidades", 503, "Indisponivel");
        var result = service.analyze("01001000", "20040002");
        assertThat(result.status()).isEqualTo(AnalysisStatus.ANALISADA);
        assertThat(result.previsao().get("status")).isEqualTo("INDISPONIVEL");
    }
    @Test void falhaViaCepPropagaParaRetry() {
        response("/ws/99999999/json/", 503, "Indisponivel");
        assertThatThrownBy(() -> service.analyze("99999999", "20040002"))
            .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }
}
