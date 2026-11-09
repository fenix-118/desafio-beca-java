package com.beca.transacoes.application.port.in;

import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;

import java.math.BigDecimal;

public interface RegistrarTransacaoUseCase {

    /** Valida, avalia e grava. Devolve a transação já APROVADA ou REJEITADA, com id. */
    Transacao registrar(RegistrarTransacaoCommand comando);

    record RegistrarTransacaoCommand(Long usuarioId, TipoTransacao tipo, BigDecimal valor,
                                     Categoria categoria, Long usuarioDestinoId, String descricao) {
    }
}
