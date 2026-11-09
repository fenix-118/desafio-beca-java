package com.beca.transacoes.domain.model;

public enum MotivoRejeicao {

    SALDO_INSUFICIENTE("Saldo insuficiente para a transação."),
    LIMITE_POR_TRANSACAO_EXCEDIDO("Valor acima do limite por transação."),
    LIMITE_DIARIO_EXCEDIDO("Valor acima do limite diário disponível."),
    SERVICO_SALDO_INDISPONIVEL("Serviço de saldo indisponível; a transação não pôde ser avaliada."),
    DESTINO_INEXISTENTE("Usuário de destino não encontrado.");

    private final String descricao;

    MotivoRejeicao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
