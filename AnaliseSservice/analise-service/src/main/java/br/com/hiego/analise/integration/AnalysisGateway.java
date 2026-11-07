package br.com.hiego.analise.integration;

import br.com.hiego.analise.filds.ExternalApiFields;
import br.com.hiego.analise.filds.AddressFields;
import br.com.hiego.analise.filds.ForecastFields;
import br.com.hiego.analise.enuns.ForecastStatus;
import br.com.hiego.analise.repository.ApiCacheRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import tools.jackson.databind.ObjectMapper;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;

@Component
public class AnalysisGateway {

    private final RestClient client;
    private final ApiCacheRepository cache;
    private final ObjectMapper mapper;
    private final String viaCepUrl;
    private final String cptecUrl;

    /** Configura o cliente HTTP, os limites de tempo e as dependências de cache e serialização. */
    public AnalysisGateway(ApiCacheRepository cache, ObjectMapper mapper,
                           @Value("${app.integrations.viacep-url}") String viaCepUrl,
                           @Value("${app.integrations.cptec-url}") String cptecUrl) {

        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.client = RestClient.builder().requestFactory(factory).build();
        this.cache = cache; this.mapper = mapper; this.viaCepUrl = viaCepUrl; this.cptecUrl = cptecUrl;
    }

    /** Consulta e converte o endereco, utilizando cache quando disponivel. */
    public Map<String, Object> findAddress(String cep) {

        String key = "analise:cep:" + cep;
        String body = cache.get(key);

        if (body == null) body = client.get().uri(viaCepUrl + "/ws/" + cep + "/json/").retrieve().body(String.class);

        if (body == null) throw new IllegalStateException("ViaCEP retornou resposta vazia");

        var json = mapper.readTree(body);
        if (json.path(ExternalApiFields.ViaCep.ERRO).asBoolean(false)) return Map.of();

        String city = json.path(ExternalApiFields.ViaCep.LOCALIDADE).asText("");
        String state = json.path(ExternalApiFields.ViaCep.UF).asText("");

        if (city.isBlank() || state.isBlank()) throw new IllegalStateException("ViaCEP retornou endereco incompleto");

        cache.put(key, body, Duration.ofDays(7));

        return Map.of(AddressFields.CEP, cep, AddressFields.CIDADE, city, AddressFields.UF, state);
    }

    /** Consulta e converte a previsao CPTEC; a service decide como tratar indisponibilidade. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> findForecast(String city, String state) throws Exception {
        String key = "analise:previsao:" + state + ":" + normalize(city);
        String cached = cache.get(key);

        if (cached != null) return mapper.readValue(cached, Map.class);

        var url = UriComponentsBuilder.fromUriString(cptecUrl + "/listaCidades")
            .queryParam(ExternalApiFields.Cptec.CITY_QUERY, city).build().encode(StandardCharsets.UTF_8).toUri();

        var cities = xml(client.get().uri(url).retrieve().body(byte[].class));
        var nodes = cities.getElementsByTagName(ExternalApiFields.Cptec.CIDADE);

        String id = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = (Element) nodes.item(i);
            if (normalize(city).equals(normalize(text(node, ExternalApiFields.Cptec.NOME))) && state.equalsIgnoreCase(text(node, ExternalApiFields.Cptec.UF))) {
                id = text(node, ExternalApiFields.Cptec.ID); break;
            }
        }

        if (id == null || !id.matches("[0-9]+")){
            return Map.of(ForecastFields.FONTE, ForecastFields.FONTE_CPTEC,
                    ForecastFields.STATUS, ForecastStatus.LOCALIDADE_NAO_ENCONTRADA.name()
            );
        }

        var forecast = xml(client.get().uri(cptecUrl + "/cidade/" + id + "/previsao.xml").retrieve().body(byte[].class));
        var days = new ArrayList<Map<String, Object>>();
        var entries = forecast.getElementsByTagName(ExternalApiFields.Cptec.PREVISAO);

        for (int i = 0; i < entries.getLength(); i++) {
            var node = (Element) entries.item(i);
            days.add(Map.of(ForecastFields.DIA, text(node, ExternalApiFields.Cptec.DIA), ForecastFields.TEMPO, text(node, ExternalApiFields.Cptec.TEMPO),
                ForecastFields.MINIMA, text(node, ExternalApiFields.Cptec.MINIMA), ForecastFields.MAXIMA, text(node, ExternalApiFields.Cptec.MAXIMA)));
        }

        if (days.isEmpty()) throw new IllegalStateException("CPTEC sem dias de previsao");

        Map<String, Object> result = Map.of(ForecastFields.FONTE, ForecastFields.FONTE_CPTEC,
                ForecastFields.STATUS, ForecastStatus.DISPONIVEL.name(),
                ForecastFields.CIDADE_ID, id, ForecastFields.DIAS, days
        );

        cache.put(key, mapper.writeValueAsString(result), Duration.ofMinutes(30));

        return result;
    }

    /** Converte os bytes em um documento XML e bloqueia DTDs e entidades externas. */
    private static Document xml(byte[] body) throws Exception {
        if (body == null || body.length == 0) throw new IllegalStateException("XML vazio");

        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
    }

    /** Lê o conteúdo da primeira tag encontrada ou retorna texto vazio quando ela não existe. */
    private static String text(Element parent, String tag) {
        var nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }
    /** Remove acentos e converte o texto para minúsculas para comparar nomes de cidades. */
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
