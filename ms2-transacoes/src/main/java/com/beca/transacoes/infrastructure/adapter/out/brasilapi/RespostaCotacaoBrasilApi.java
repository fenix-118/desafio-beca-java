package com.beca.transacoes.infrastructure.adapter.out.brasilapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta de GET /cambio/v1/cotacao/{moeda}/{data}. Exemplo real (domingo, 08/11, devolve a de sexta):
 * {"cotacoes":[...,{"cotacao_venda":6.1317,"tipo_boletim":"FECHAMENTO PTAX",...}],"moeda":"EUR","data":"2026-11-06"}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespostaCotacaoBrasilApi(List<Boletim> cotacoes, String moeda, LocalDate data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Boletim(@JsonProperty("cotacao_venda") BigDecimal cotacaoVenda,
                          @JsonProperty("tipo_boletim") String tipoBoletim) {
    }
}
