package com.beca.transacoes.application.port.out;

import com.beca.transacoes.domain.model.Transacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface TransacaoRepositoryPort {

    /** Grava uma transação já decidida (APROVADA ou REJEITADA). */
    Transacao salvar(Transacao transacao);

    Optional<Transacao> buscarPorId(Long id);

    /** Soma das saídas APROVADAS do usuário com criadaEm em [inicio, fim). Zero se não houver. */
    BigDecimal somarSaidasAprovadas(Long usuarioId, Instant inicio, Instant fim);
}
