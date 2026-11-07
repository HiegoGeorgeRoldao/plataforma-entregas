package br.com.hiego.entregas.exception;

import br.com.hiego.entregas.entity.Entrega;
public class EntregaNaoEncontradaException extends RuntimeException {
    public EntregaNaoEncontradaException() {
        super("Entrega nao encontrada");
    }
}
