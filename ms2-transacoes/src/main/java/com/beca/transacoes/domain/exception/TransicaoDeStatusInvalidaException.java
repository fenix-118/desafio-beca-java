package com.beca.transacoes.domain.exception;

public class TransicaoDeStatusInvalidaException extends RuntimeException {

    public TransicaoDeStatusInvalidaException(String mensagem) {
        super(mensagem);
    }
}
