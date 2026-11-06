package com.beca.transacoes.application.port.out;

import com.beca.transacoes.domain.model.Transacao;

import java.util.Optional;

public interface TransacaoRepositoryPort {

    /** Grava uma transação já decidida (APROVADA ou REJEITADA). */
    Transacao salvar(Transacao transacao);

    Optional<Transacao> buscarPorId(Long id);
}
