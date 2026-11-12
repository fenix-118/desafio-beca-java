package com.beca.transacoes.application.service;

import com.beca.transacoes.domain.exception.DadosInvalidosException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Período de consulta em dias de São Paulo, com as mesmas regras na análise de despesas (pedido 5)
 * e no relatório (pedido 7): sem datas, o mês atual; só uma data, 400; no máximo 366 dias.
 */
final class PeriodoSaoPaulo {

    static final String PERIODO_INCOMPLETO =
            "Informe 'inicio' e 'fim' juntos, ou nenhum dos dois para consultar o mês atual.";
    static final String PERIODO_INVALIDO = "A data 'inicio' deve ser anterior ou igual à data 'fim'.";

    /** Um ano: evita carregar vários anos de transações na memória de uma vez. */
    static final int PERIODO_MAXIMO_DIAS = 366;
    static final String PERIODO_LONGO_DEMAIS =
            "O período pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.";

    private final LocalDate inicio;
    private final LocalDate fim;

    private PeriodoSaoPaulo(LocalDate inicio, LocalDate fim) {
        this.inicio = inicio;
        this.fim = fim;
    }

    static PeriodoSaoPaulo resolver(LocalDate inicio, LocalDate fim, Clock relogio) {
        if (inicio == null && fim == null) {
            YearMonth mesAtual = YearMonth.now(relogio.withZone(DiaSaoPaulo.FUSO));
            return new PeriodoSaoPaulo(mesAtual.atDay(1), mesAtual.atEndOfMonth());
        }
        if (inicio == null || fim == null) {
            throw new DadosInvalidosException(PERIODO_INCOMPLETO);
        }
        if (inicio.isAfter(fim)) {
            throw new DadosInvalidosException(PERIODO_INVALIDO);
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new DadosInvalidosException(PERIODO_LONGO_DEMAIS);
        }
        return new PeriodoSaoPaulo(inicio, fim);
    }

    LocalDate inicio() { return inicio; }
    LocalDate fim() { return fim; }

    /** Início do primeiro dia em São Paulo. */
    Instant de() { return inicio.atStartOfDay(DiaSaoPaulo.FUSO).toInstant(); }

    /** O fim é incluído, por isso o limite (exclusivo) é o início do dia seguinte. */
    Instant ate() { return fim.plusDays(1).atStartOfDay(DiaSaoPaulo.FUSO).toInstant(); }
}
