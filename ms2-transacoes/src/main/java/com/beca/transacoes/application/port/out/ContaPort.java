package com.beca.transacoes.application.port.out;

import com.beca.transacoes.domain.model.SituacaoConta;

/** Fonte do saldo e dos limites da conta. No pedido 2 o adaptador passa a ser a API Mock. */
public interface ContaPort {

    /**
     * @throws ServicoSaldoIndisponivelException se a fonte do saldo não responder
     */
    SituacaoConta consultar(Long usuarioId);
}
