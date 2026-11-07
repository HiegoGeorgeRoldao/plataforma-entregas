package br.com.hiego.entregas.repository;

import br.com.hiego.entregas.entity.Entrega;
import br.com.hiego.entregas.enuns.StatusEntrega;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

/** Filtros combináveis para a consulta paginada de entregas. */
public final class EntregaSpecifications {
    private EntregaSpecifications() {}

    /**
     * Combina o filtro obrigatorio de usuario com filtros opcionais de estado e busca.
     *
     * @param usuario dono das entregas
     * @param busca trecho de CEP ou UUID, com hifens e cerquilhas ignorados
     * @param status estado desejado, ou {@code null} para todos
     * @return filtro para a consulta paginada do Spring Data JPA
     */
    public static Specification<Entrega> paraConsulta(UUID usuario, String busca, StatusEntrega status) {
        return Specification.allOf(pertenceAoUsuario(usuario), comStatus(status), correspondeABusca(busca));
    }

    private static Specification<Entrega> pertenceAoUsuario(UUID usuario) {
        return (root, query, criteria) -> criteria.equal(root.get("usuarioId"), usuario);
    }

    private static Specification<Entrega> comStatus(StatusEntrega status) {
        return status == null ? Specification.unrestricted()
            : (root, query, criteria) -> criteria.equal(root.get("status"), status);
    }

    /** Trata caracteres especiais do LIKE como texto literal e normaliza o UUID para a busca. */
    private static Specification<Entrega> correspondeABusca(String busca) {
        if (busca == null || busca.isBlank()) return Specification.unrestricted();

        String pattern = "%" + busca.trim().replace("-", "").replace("#", "")
            .toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
            .replace("_", "\\_") + "%";

        return (root, query, criteria) -> {
            var idSemHifens = criteria.function("replace", String.class,
                root.get("id").cast(String.class),
                    criteria.literal("-"),
                    criteria.literal("")
            );

            return criteria.or(
                criteria.like(idSemHifens, pattern, '\\'),
                criteria.like(root.get("cepOrigem"), pattern, '\\'),
                criteria.like(root.get("cepDestino"), pattern, '\\')
            );
        };
    }
}
