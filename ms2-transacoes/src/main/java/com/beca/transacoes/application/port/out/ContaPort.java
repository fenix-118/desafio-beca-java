package com.beca.transacoes.application.port.out;

import com.beca.transacoes.domain.model.SituacaoConta;

import java.math.BigDecimal;
import java.util.Optional;

/** Fonte do saldo e dos limites da conta do usuário (a API Mock). */
public interface ContaPort {

    /**
     * @return a situação atual da conta, ou vazio se o usuário não tem conta
     * @throws ServicoSaldoIndisponivelException se a fonte do saldo não responder ou devolver dados inválidos
     */
    Optional<SituacaoConta> consultar(Long usuarioId);

    /**
     * Grava o novo saldo da conta. Chamado antes de aprovar: se falhar, a transação não é aprovada.
     *
     * @throws ServicoSaldoIndisponivelException se a fonte do saldo não responder ou a conta sumir
     */
    void atualizarSaldo(Long usuarioId, BigDecimal novoSaldo);
}
