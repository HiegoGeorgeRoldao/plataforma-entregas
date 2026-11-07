package br.com.hiego.entregas.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record PaginaEntregas(List<EntregaResponse> itens, long total, int pagina, int tamanho) {

    /**
     * Adapta a pagina do Spring Data ao formato de resposta usado pelo frontend.
     *
     * @param pagina resultados e metadados da consulta
     * @return resposta com {@code itens}, {@code total}, {@code pagina} e {@code tamanho}
     */
    public static PaginaEntregas from(Page<EntregaResponse> pagina) {
        return new PaginaEntregas(pagina.getContent(), pagina.getTotalElements(),
            pagina.getNumber(), pagina.getSize());
    }

}
