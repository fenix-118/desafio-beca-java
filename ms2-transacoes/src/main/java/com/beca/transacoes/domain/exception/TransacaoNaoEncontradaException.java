package com.beca.transacoes.domain.exception;

public class TransacaoNaoEncontradaException extends RuntimeException {

    public TransacaoNaoEncontradaException(Long id) {
        super("Transação " + id + " não encontrada.");
    }
}
