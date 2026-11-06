package com.beca.usuarios.domain.exception;

public class EmailJaCadastradoException extends RuntimeException {

    public static final String MENSAGEM = "Já existe um usuário com o e-mail informado.";

    public EmailJaCadastradoException() {
        super(MENSAGEM);
    }
}