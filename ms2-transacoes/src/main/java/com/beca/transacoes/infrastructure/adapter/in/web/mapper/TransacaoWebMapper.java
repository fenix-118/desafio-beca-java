package com.beca.transacoes.infrastructure.adapter.in.web.mapper;

import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase.RegistrarTransacaoCommand;
import com.beca.transacoes.domain.model.Transacao;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.RegistrarTransacaoRequest;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.TransacaoResponse;

public final class TransacaoWebMapper {

    /** Moeda base de todas as transações (dúvida 10 do refinamento). */
    static final String MOEDA_BASE = "BRL";

    private TransacaoWebMapper() {
    }

    public static RegistrarTransacaoCommand paraComando(RegistrarTransacaoRequest r) {
        return new RegistrarTransacaoCommand(r.usuarioId(), r.tipo(), r.valor(), r.categoria(),
                r.usuarioDestinoId(), r.descricao());
    }

    public static TransacaoResponse paraResposta(Transacao t) {
        return new TransacaoResponse(
                t.getId(),
                t.getUsuarioId(),
                t.getTipo(),
                t.getValor(),
                MOEDA_BASE,
                t.getCategoria(),
                t.getUsuarioDestinoId(),
                t.getDescricao(),
                t.getStatus(),
                t.getMotivoRejeicao(),
                t.getMotivoRejeicao() == null ? null : t.getMotivoRejeicao().getDescricao(),
                t.getCriadaEm());
    }
}
