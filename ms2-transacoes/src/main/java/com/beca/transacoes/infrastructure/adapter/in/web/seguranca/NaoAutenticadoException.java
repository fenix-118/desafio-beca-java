package com.beca.transacoes.infrastructure.adapter.in.web.seguranca;

/** A requisição chegou ao controller sem o usuário autenticado (o filtro não rodou). */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException() {
        super(AutenticacaoBasicaFilter.SEM_CREDENCIAIS);
    }
}
