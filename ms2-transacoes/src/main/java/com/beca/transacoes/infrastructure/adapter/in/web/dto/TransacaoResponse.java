package com.beca.transacoes.infrastructure.adapter.in.web.dto;

import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;

import java.math.BigDecimal;
import java.time.Instant;

public record TransacaoResponse(
        Long id,
        Long usuarioId,
        TipoTransacao tipo,
        BigDecimal valor,
        String moeda,
        Categoria categoria,
        Long usuarioDestinoId,
        String descricao,
        StatusTransacao status,
        MotivoRejeicao motivoRejeicao,
        String motivoRejeicaoDescricao,
        Instant criadaEm) {
}
