package com.beca.transacoes.application.port.out;

import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TransacaoRepositoryPort {

    /** Grava uma transação já decidida (APROVADA ou REJEITADA). */
    Transacao salvar(Transacao transacao);

    Optional<Transacao> buscarPorId(Long id);

    /** Soma das saídas APROVADAS do usuário com criadaEm em [inicio, fim). Zero se não houver. */
    BigDecimal somarSaidasAprovadas(Long usuarioId, Instant inicio, Instant fim);

    /**
     * Transações do usuário com criadaEm em [inicio, fim), da mais recente para a mais antiga.
     * inicio e fim nulos significam sem limite naquele lado.
     */
    List<Transacao> listar(Long usuarioId, Instant inicio, Instant fim, Set<TipoTransacao> tipos);
}
