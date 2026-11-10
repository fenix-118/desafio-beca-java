package com.beca.transacoes.domain.exception;

public class ContaNaoEncontradaException extends RuntimeException {

    public ContaNaoEncontradaException(Long usuarioId) {
        super("O usuário " + usuarioId + " não tem conta cadastrada na API de saldo.");
    }
}
