package com.beca.transacoes.application.port.out;

/** Lançada pelo adaptador de conta quando a fonte do saldo está fora do ar ou demora demais. */
public class ServicoSaldoIndisponivelException extends RuntimeException {

    public ServicoSaldoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
