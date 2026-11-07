package br.com.hiego.analise.repository;

import br.com.hiego.analise.entity.Analise;
import java.util.Optional;


public interface AnaliseRepository {
    /** Busca a analise da entrega somente quando pertence ao usuario informado. */
    Optional<Analise> findByEntregaIdAndUsuarioId(String entregaId, String usuarioId);

    /** Insere uma analise nova; nao sobrescreve uma entrega ja analisada. */
    void insert(Analise analise);
}
