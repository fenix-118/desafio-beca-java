package com.beca.transacoes.application.port.out;

/** Lançada quando o MS1 está fora do ar, demora demais ou responde com erro. */
public class ServicoUsuariosIndisponivelException extends RuntimeException {

    public ServicoUsuariosIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
