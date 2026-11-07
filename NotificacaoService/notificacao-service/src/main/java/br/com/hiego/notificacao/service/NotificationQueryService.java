package br.com.hiego.notificacao.service;

import br.com.hiego.notificacao.dto.NotificationView;
import br.com.hiego.notificacao.exception.NotificationNotFoundException;
import br.com.hiego.notificacao.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta notificacoes e controla sua leitura no escopo do usuario local configurado. */
@Service
public class NotificationQueryService {
    private final NotificationRepository notifications;
    private final UUID usuario;

    /**
     * Define a persistencia e o usuario usado nas consultas do MVP.
     *
     * @param notifications repositorio de notificacoes
     * @param usuario identidade fixa de {@code app.usuario-local-id}; nao representa autenticacao
     */
    public NotificationQueryService(NotificationRepository notifications,
                                    @Value("${app.usuario-local-id}") UUID usuario) {
        this.notifications = notifications;
        this.usuario = usuario;
    }

    /**
     * Lista as notificacoes mais recentes do usuario configurado, com filtro opcional por entrega.
     *
     * @param limit quantidade maxima de resultados, validada pela controller
     * @param entregaId entrega a filtrar, ou {@code null} para todas
     * @return notificacoes ordenadas por criacao decrescente e identificador
     */
    @Transactional(readOnly = true)
    public List<NotificationView> list(int limit, UUID entregaId) {
        return notifications.list(usuario, entregaId, limit);
    }

    /**
     * Marca como lida uma notificacao do usuario configurado.
     * Repetir a operacao para uma notificacao ja lida continua sendo permitido.
     *
     * @param id identificador da notificacao
     * @throws NotificationNotFoundException se o registro nao existir ou pertencer a outro usuario
     */
    @Transactional
    public void markRead(UUID id) {
        if (!notifications.markRead(id, usuario)) throw new NotificationNotFoundException();
    }
}
