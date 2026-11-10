package com.beca.transacoes.infrastructure.adapter.out.mockapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Uma linha do recurso "contas" da API Mock, como ela vem no JSON. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContaMockApi(String id, Long usuarioId, BigDecimal saldo,
                    BigDecimal limitePorTransacao, BigDecimal limiteDiario) {
}
