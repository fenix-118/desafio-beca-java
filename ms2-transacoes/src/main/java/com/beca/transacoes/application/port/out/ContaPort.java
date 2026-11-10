package com.beca.transacoes.application.port.out;

import com.beca.transacoes.domain.model.SituacaoConta;

import java.util.Optional;

/** Fonte do saldo e dos limites da conta do usuário (a API Mock). */
public interface ContaPort {

    /**
     * @return a situação atual da conta, ou vazio se o usuário não tem conta
     * @throws ServicoSaldoIndisponivelException se a fonte do saldo não responder ou devolver dados inválidos
     */
    Optional<SituacaoConta> consultar(Long usuarioId);
}
