package br.com.hiego.analise.service;

import br.com.hiego.analise.entity.Analise;
import br.com.hiego.analise.repository.AnaliseRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnaliseQueryService {
    private final AnaliseRepository analises;
    private final String usuarioId;

    /** Recebe o repositório de análises e o identificador do usuário configurado no serviço. */
    public AnaliseQueryService(AnaliseRepository analises,
                              @Value("${app.usuario-local-id}") String usuarioId) {
        this.analises = analises;
        this.usuarioId = usuarioId;
    }

    /** Consulta a analise da entrega sem permitir acesso aos dados de outro usuario. */
    public Optional<Analise> findByEntregaId(UUID entregaId) {
        return analises.findByEntregaIdAndUsuarioId(entregaId.toString(), usuarioId);
    }
}
