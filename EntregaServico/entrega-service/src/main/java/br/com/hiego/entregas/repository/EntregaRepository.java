package br.com.hiego.entregas.repository;

import br.com.hiego.entregas.entity.Entrega;
import br.com.hiego.entregas.dto.ResumoEntregas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface EntregaRepository extends JpaRepository<Entrega, UUID>, JpaSpecificationExecutor<Entrega> {
    /** Consulta uma entrega pelo identificador sem expor registros de outro usuario. */
    Optional<Entrega> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Verifica a titularidade antes de consultar o historico de eventos. */
    boolean existsByIdAndUsuarioId(UUID id, UUID usuarioId);

    /**
     * Agrega em uma unica consulta os totais por estado do usuario informado.
     *
     * @param usuario dono das entregas
     * @return resumo com zero nos estados sem ocorrencias
     */
    @Query("""
        select new br.com.hiego.entregas.dto.ResumoEntregas(
            count(e),
            coalesce(sum(case when e.status = br.com.hiego.entregas.enuns.StatusEntrega.ANALISADA then 1L else 0L end), 0L),
            coalesce(sum(case when e.status = br.com.hiego.entregas.enuns.StatusEntrega.SOLICITADA then 1L else 0L end), 0L),
            coalesce(sum(case when e.status = br.com.hiego.entregas.enuns.StatusEntrega.REJEITADA then 1L else 0L end), 0L))
        from Entrega e
        where e.usuarioId = :usuario
        """)
    ResumoEntregas resumo(@Param("usuario") UUID usuario);
}
