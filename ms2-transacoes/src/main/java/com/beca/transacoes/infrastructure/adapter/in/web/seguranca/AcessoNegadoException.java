package com.beca.transacoes.infrastructure.adapter.in.web.seguranca;

/** O usuário autenticado tentou ver ou registrar dados de outro usuário (tarefa 6.3). */
public class AcessoNegadoException extends RuntimeException {

    public static final String MENSAGEM = "Acesso negado: o usuário autenticado só acessa as próprias transações.";

    public AcessoNegadoException() {
        super(MENSAGEM);
    }
}
