package com.beca.transacoes.domain.exception;

public class UsuarioNaoEncontradoException extends RuntimeException {

    public UsuarioNaoEncontradoException(Long id) {
        super("Usuário " + id + " não encontrado.");
    }
}
