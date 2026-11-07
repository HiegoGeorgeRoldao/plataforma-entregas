package br.com.hiego.entregas.service;

import br.com.hiego.entregas.dto.EntregaResponse;
import br.com.hiego.entregas.dto.EventoEntregaResponse;
import br.com.hiego.entregas.dto.PaginaEntregas;
import br.com.hiego.entregas.dto.ResumoEntregas;
import br.com.hiego.entregas.enuns.StatusEntrega;
import br.com.hiego.entregas.exception.EntregaNaoEncontradaException;
import br.com.hiego.entregas.repository.EntregaRepository;
import br.com.hiego.entregas.repository.EntregaSpecifications;
import br.com.hiego.entregas.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EntregaQueryService {
    private final EntregaRepository entregas;
    private final OutboxRepository outbox;
    private final UUID usuario;

    public EntregaQueryService(EntregaRepository entregas, OutboxRepository outbox,
                              @Value("${app.usuario-local-id}") UUID usuario) {
        this.entregas = entregas;
        this.outbox = outbox;
        this.usuario = usuario;
    }

    /**
     * Lista apenas as entregas do usuario local, combinando filtros opcionais
     * com a pagina e a ordenacao recebidas da controller.
     *
     * @param busca trecho de CEP ou identificador, opcional
     * @param status estado desejado, opcional
     * @param pageable numero, tamanho e ordenacao da pagina
     * @return itens e metadados da pagina no formato da API
     */
    public PaginaEntregas listar(String busca, StatusEntrega status, Pageable pageable) {
        var pagina = entregas.findAll(EntregaSpecifications.paraConsulta(usuario, busca, status), pageable)
            .map(EntregaResponse::from);
        return PaginaEntregas.from(pagina);
    }

    /**
     * Conta as entregas do usuario local por estado.
     *
     * @return totais de solicitacoes, analisadas, pendentes e rejeitadas
     */
    public ResumoEntregas resumo() {
        return entregas.resumo(usuario);
    }

    /**
     * Retorna o historico de eventos da Outbox de uma entrega do usuario local.
     * Confere a titularidade antes de consultar os eventos.
     *
     * @param entregaId identificador da entrega
     * @return eventos em ordem de criacao
     * @throws EntregaNaoEncontradaException se a entrega nao existir ou pertencer a outro usuario
     */
    public List<EventoEntregaResponse> eventos(UUID entregaId) {
        if (!entregas.existsByIdAndUsuarioId(entregaId, usuario)) throw new EntregaNaoEncontradaException();
        return outbox.eventos(entregaId, usuario);
    }
}
