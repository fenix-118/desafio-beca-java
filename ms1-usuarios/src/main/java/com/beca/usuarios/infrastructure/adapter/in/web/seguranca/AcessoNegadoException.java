package com.beca.usuarios.infrastructure.adapter.in.web.seguranca;

/** O usuário autenticado tentou ver, alterar ou excluir outro usuário. */
public class AcessoNegadoException extends RuntimeException {

    public static final String MENSAGEM = "Acesso negado: o usuário autenticado só acessa a própria conta.";

    public AcessoNegadoException() {
        super(MENSAGEM);
    }
}
