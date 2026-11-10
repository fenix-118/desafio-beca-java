package com.beca.transacoes.domain.model;

public enum MotivoRejeicao {

    SALDO_INSUFICIENTE("Saldo insuficiente para a transação."),
    LIMITE_POR_TRANSACAO_EXCEDIDO("Valor acima do limite por transação."),
    LIMITE_DIARIO_EXCEDIDO("Valor acima do limite diário disponível."),
    SERVICO_SALDO_INDISPONIVEL("Serviço de saldo indisponível; a transação não pôde ser avaliada."),
    DESTINO_INEXISTENTE("Usuário de destino não encontrado."),
    SERVICO_USUARIOS_INDISPONIVEL("Serviço de usuários indisponível ao conferir o destino; a transação não foi aprovada e pode ser refeita."),
    CONTA_INEXISTENTE("O usuário não tem conta cadastrada na API de saldo.");

    private final String descricao;

    MotivoRejeicao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
