package br.com.hiego.entregas.controller;

import br.com.hiego.entregas.dto.CotaDiariaResponse;
import br.com.hiego.entregas.dto.EntregaResponse;
import br.com.hiego.entregas.dto.EventoEntregaResponse;
import br.com.hiego.entregas.dto.PaginaEntregas;
import br.com.hiego.entregas.dto.ResumoEntregas;
import br.com.hiego.entregas.dto.SolicitarEntregaRequest;
import br.com.hiego.entregas.enuns.StatusEntrega;
import br.com.hiego.entregas.service.EntregaQueryService;
import br.com.hiego.entregas.service.EntregaService;
import br.com.hiego.entregas.service.RateLimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.UUID;

@RestController
@RequestMapping("/entregas")
public class EntregaController {
    private static final Sort ORDEM_LISTAGEM = Sort.by(
        Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final EntregaService service;
    private final RateLimitService rateLimit;
    private final EntregaQueryService queries;

    public EntregaController(EntregaService service, RateLimitService rateLimit, EntregaQueryService queries) {
        this.service = service;
        this.rateLimit = rateLimit;
        this.queries = queries;
    }

    /**
     * Consome a cota diaria e solicita uma entrega, retornando a localizacao do recurso.
     * A gravacao do evento na Outbox e feita pelo service de entregas.
     *
     * @param request CEPs validados da solicitacao
     * @return resposta HTTP 202 com a entrega criada
     */
    @PostMapping
    public ResponseEntity<EntregaResponse> solicitar(@Valid @RequestBody SolicitarEntregaRequest request) {
        rateLimit.acquire();
        EntregaResponse response = service.solicitar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.accepted().location(location).body(response);
    }

    /** Consulta uma entrega pelo identificador, respeitando o usuario local. */
    @GetMapping("/{id}")
    public EntregaResponse consultar(@PathVariable UUID id) { return service.consultar(id); }

    /**
     * Valida os parametros de busca e paginacao e fixa a ordenacao por criacao e ID.
     *
     * @return pagina de entregas no formato esperado pelo frontend
     */
    @GetMapping
    public PaginaEntregas listar(@RequestParam(required = false) @Size(max = 80) String busca,
                                 @RequestParam(required = false) StatusEntrega status,
                                 @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int pagina,
                                 @RequestParam(defaultValue = "10") @Min(1) @Max(50) int tamanho) {
        return queries.listar(busca, status, PageRequest.of(pagina, tamanho, ORDEM_LISTAGEM));
    }

    /** Retorna os totais de entregas do usuario local por estado. */
    @GetMapping("/resumo")
    public ResumoEntregas resumo() { return queries.resumo(); }

    /** Consulta o uso atual da cota diaria de solicitacoes. */
    @GetMapping("/cota")
    public CotaDiariaResponse cota() { return rateLimit.consultar(); }

    /** Lista os eventos de Outbox de uma entrega do usuario local. */
    @GetMapping("/{id}/eventos")
    public java.util.List<EventoEntregaResponse> eventos(@PathVariable UUID id) { return queries.eventos(id); }
}
