package br.com.hiego.entregas.service;

import br.com.hiego.entregas.dto.CotaDiariaResponse;
import br.com.hiego.entregas.repository.RateLimitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.UUID;

@Service
public class RateLimitService {
    private final RateLimitRepository repository;
    private final UUID usuario;
    private final boolean enabled;
    private final int limit;

    public RateLimitService(RateLimitRepository repository, @Value("${app.usuario-local-id}") UUID usuario,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.limit-per-day:20}") int limit) {
        this.repository = repository; this.usuario = usuario; this.enabled = enabled; this.limit = limit;
        if (limit < 1) throw new IllegalArgumentException("Cota deve ser positiva");
    }

    /**
     * Consome uma unidade da cota diaria do usuario no Redis antes da solicitacao.
     * A chave expira no inicio do proximo dia no fuso de Sao Paulo.
     *
     * @throws ResponseStatusException com HTTP 429 quando a cota acabou ou
     *         HTTP 503 quando o Redis esta indisponivel
     */
    public void acquire() {
        if (!enabled) return;
        var now = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        long ttl = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.getZone())).toMillis();
        boolean allowed;
        try {
            allowed = repository.acquire("entrega:quota:" + usuario + ":" + now.toLocalDate(), limit, ttl);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Controle de cota indisponivel");
        }
        if (!allowed)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite diario de solicitacoes atingido");
    }

    /**
     * Consulta a cota usada e calcula o saldo e o proximo horario de renovacao.
     *
     * @return estado da cota diaria, inclusive quando o controle esta desabilitado
     * @throws ResponseStatusException com HTTP 503 quando o Redis esta indisponivel
     */
    public CotaDiariaResponse consultar() {
        var now = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        var resetsAt = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant();
        if (!enabled) return new CotaDiariaResponse(limit, 0, limit, resetsAt, false);
        try {
            long used = repository.used("entrega:quota:" + usuario + ":" + now.toLocalDate());
            return new CotaDiariaResponse(limit, used, Math.max(0, limit - used), resetsAt, true);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Controle de cota indisponivel");
        }
    }
}
