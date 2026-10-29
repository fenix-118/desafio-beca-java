package com.beca.usuarios.domain.exception;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("Já existe um usuário com o e-mail informado.");
    }
}
