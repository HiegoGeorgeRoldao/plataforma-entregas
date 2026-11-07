package br.com.hiego.notificacao.controller;

import br.com.hiego.notificacao.dto.NotificationView;
import br.com.hiego.notificacao.service.NotificationQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Expoe consultas e marcacao de leitura das notificacoes pelo HTTP. */
@RestController
@RequestMapping("/notificacoes")
public class NotificationController {
    private final NotificationQueryService notifications;

    /**
     * @param notifications service responsavel por consultas e leitura das notificacoes
     */
    public NotificationController(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    /**
     * Atende {@code GET /notificacoes}, validando o limite antes de consultar o service.
     *
     * @param limit quantidade maxima entre 1 e 100, com padrao 50
     * @param entregaId filtro opcional pelo identificador da entrega
     * @return lista de notificacoes do usuario configurado, com HTTP 200
     * @throws ResponseStatusException com HTTP 400 se o limite estiver fora do intervalo
     */
    @GetMapping
    public List<NotificationView> list(@RequestParam(defaultValue = "50") int limit,
                                       @RequestParam(required = false) UUID entregaId) {
        if (limit < 1 || limit > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit deve estar entre 1 e 100");
        return notifications.list(limit, entregaId);
    }

    /**
     * Atende {@code PATCH /notificacoes/{id}/leitura} e retorna HTTP 204 apos marcar a leitura.
     * Notificacoes inexistentes ou de outro usuario resultam em HTTP 404.
     *
     * @param id identificador da notificacao
     * @throws br.com.hiego.notificacao.exception.NotificationNotFoundException se o registro nao for encontrado
     */
    @PatchMapping("/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable UUID id) {
        notifications.markRead(id);
    }
}
