package com.beca.transacoes.infrastructure.adapter.in.web.dto;

import com.beca.transacoes.domain.model.Categoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ResumoDespesasResponse(
        Long usuarioId,
        LocalDate inicio,
        LocalDate fim,
        String moeda,
        BigDecimal totalGeral,
        int quantidade,
        List<CategoriaResponse> porCategoria,
        List<DiaResponse> porDia,
        List<MesResponse> porMes) {

    public record CategoriaResponse(Categoria categoria, BigDecimal total, int quantidade) {
    }

    public record DiaResponse(LocalDate dia, BigDecimal total, int quantidade) {
    }

    /** @param mes no formato aaaa-mm */
    public record MesResponse(String mes, BigDecimal total, int quantidade) {
    }
}
