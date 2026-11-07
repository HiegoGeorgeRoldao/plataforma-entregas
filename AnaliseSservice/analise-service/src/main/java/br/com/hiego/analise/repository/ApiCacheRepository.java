package br.com.hiego.analise.repository;

import java.time.Duration;

/** Cache textual das respostas externas; ausencia ou falha permite consultar a API. */
public interface ApiCacheRepository {
    /** Retorna o valor ou null quando nao houver entrada disponivel. */
    String get(String key);

    /** Armazena o valor com o prazo informado, sem impedir a analise se o cache falhar. */
    void put(String key, String value, Duration ttl);
}
