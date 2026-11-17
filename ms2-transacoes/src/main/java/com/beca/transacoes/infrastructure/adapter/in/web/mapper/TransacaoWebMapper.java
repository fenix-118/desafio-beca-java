package com.beca.transacoes.infrastructure.adapter.in.web.mapper;

import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase.RegistrarTransacaoCommand;
import com.beca.transacoes.domain.model.MoedaEstrangeira;
import com.beca.transacoes.domain.model.Transacao;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.ConversaoResponse;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.RegistrarTransacaoRequest;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.TransacaoResponse;

import java.util.Arrays;
import java.util.List;

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
                t.getCriadaEm(),
                conversoes(t));
    }

    /** Uma linha por moeda, sempre na mesma ordem; sem cotação guardada, a moeda aparece como indisponível. */
    static List<ConversaoResponse> conversoes(Transacao t) {
        return Arrays.stream(MoedaEstrangeira.values())
                .map(moeda -> t.cotacao(moeda)
                        .map(c -> new ConversaoResponse(moeda, true, c.converter(t.getValor()), c.taxa(), c.data()))
                        .orElseGet(() -> ConversaoResponse.indisponivel(moeda)))
                .toList();
    }
}
