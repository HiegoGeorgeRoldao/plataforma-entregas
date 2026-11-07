package br.com.hiego.analise.controller;

import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.service.AnaliseQueryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analises")
public class AnaliseController {
    private final AnaliseQueryService service;

    public AnaliseController(AnaliseQueryService service) {
        this.service = service;
    }

    /** Retorna a analise da entrega com o mesmo contrato JSON consumido pelo frontend. */
    @GetMapping("/{entregaId}")
    public ResponseEntity<Analise> get(@PathVariable UUID entregaId) {
        return service.findByEntregaId(entregaId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
