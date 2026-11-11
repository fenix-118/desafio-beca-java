package com.beca.transacoes.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SaldoResponse(
        Long usuarioId,
        BigDecimal saldo,
        String moeda,
        BigDecimal limitePorTransacao,
        BigDecimal limiteDiario,
        BigDecimal gastoHoje,
        BigDecimal limiteDiarioDisponivel,
        Instant consultadoEm) {
}
