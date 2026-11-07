package br.com.hiego.notificacao.repository;

import br.com.hiego.notificacao.dto.NotificationView;
import br.com.hiego.notificacao.enuns.NotificationStatus;
import br.com.hiego.notificacao.filds.NotificationFields;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Consulta notificacoes do usuario com filtro opcional por entrega.
     * O cast SQL e o tipo JDBC permitem vincular um UUID nulo sem ambiguidade no PostgreSQL.
     *
     * @param usuario dono das notificacoes
     * @param entregaId entrega a filtrar, ou {@code null} para todas
     * @param limit quantidade maxima de resultados
     * @return notificacoes mais recentes, com desempate por identificador
     */
    public List<NotificationView> list(UUID usuario, UUID entregaId, int limit) {
        return jdbc.sql("""
                SELECT 
                        id, 
                        entrega_id, 
                        status, 
                        mensagem, 
                        lida, 
                        created_at
                    FROM notificacao
                WHERE usuario_id = :usuarioId
                  AND (
                        CAST(:entregaId AS uuid) IS NULL 
                        OR entrega_id = :entregaId
                      )
                ORDER BY created_at DESC, id
                LIMIT :limit
                """)
            .param("usuarioId", usuario)
            .param("entregaId", entregaId, Types.OTHER)
            .param("limit", limit)
            .query(NotificationRepository::mapNotification)
            .list();
    }

    /** Marca como lida somente uma notificacao do usuario e informa se o registro foi encontrado. */
    public boolean markRead(UUID id, UUID usuario) {
        return jdbc.sql("""
                UPDATE notificacao 
                SET lida = true
                WHERE id = :id 
                AND usuario_id = :usuarioId
                """)
            .param("id", id)
            .param("usuarioId", usuario)
            .update() > 0;
    }

    /** Insere uma notificacao, preservando a restricao de uma notificacao por entrega. */
    public void insert(UUID entregaId, UUID usuario, NotificationStatus status, String mensagem) {
        jdbc.sql("""
                INSERT INTO notificacao(id, entrega_id, usuario_id, status, mensagem)
                VALUES (:id, :entregaId, :usuarioId, :status, :mensagem)
                ON CONFLICT(entrega_id) DO NOTHING
                """)
            .param("id", UUID.randomUUID())
            .param("entregaId", entregaId)
            .param("usuarioId", usuario)
            .param("status", status.name())
            .param("mensagem", mensagem)
            .update();
    }

    private static NotificationView mapNotification(ResultSet rs, int row) throws SQLException {
        return new NotificationView(rs.getObject(NotificationFields.ID, UUID.class),
            rs.getObject(NotificationFields.ENTREGA_ID, UUID.class), rs.getString(NotificationFields.STATUS),
            rs.getString(NotificationFields.MENSAGEM), rs.getBoolean(NotificationFields.LIDA),
            rs.getTimestamp(NotificationFields.CREATED_AT).toInstant());
    }
}
