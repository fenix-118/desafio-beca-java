package com.beca.transacoes.application.port.in;

import com.beca.transacoes.domain.model.Transacao;

public interface BuscarTransacaoUseCase {

    Transacao buscarPorId(Long id);
}
