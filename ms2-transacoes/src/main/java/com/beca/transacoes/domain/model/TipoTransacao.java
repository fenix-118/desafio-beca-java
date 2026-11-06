package com.beca.transacoes.domain.model;

public enum TipoTransacao {

    DEPOSITO(false),
    RETIRADA(true),
    TRANSFERENCIA(true),
    COMPRA(true);

    private final boolean saida;

    TipoTransacao(boolean saida) {
        this.saida = saida;
    }

    /** Saída é tudo que tira dinheiro da conta: retirada, transferência enviada e compra. */
    public boolean isSaida() {
        return saida;
    }
}
