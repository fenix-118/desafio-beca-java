package com.beca.usuarios.domain.exception;

/**
 * E-mail ou senha não conferem. A mesma mensagem vale para e-mail inexistente e senha errada,
 * para quem tenta não descobrir quais e-mails estão cadastrados.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public static final String MENSAGEM = "E-mail ou senha inválidos.";

    public CredenciaisInvalidasException() {
        super(MENSAGEM);
    }
}
