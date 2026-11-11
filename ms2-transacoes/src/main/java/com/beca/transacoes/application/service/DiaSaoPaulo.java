package com.beca.transacoes.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** O "dia" do limite diário é o dia em São Paulo, não o dia UTC (dúvida 11 do refinamento). */
final class DiaSaoPaulo {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final Instant inicio;
    private final Instant fim;

    private DiaSaoPaulo(Instant inicio, Instant fim) {
        this.inicio = inicio;
        this.fim = fim;
    }

    /** O dia de hoje em São Paulo, como o intervalo [início do dia, início de amanhã). */
    static DiaSaoPaulo hoje(Clock relogio) {
        LocalDate hoje = LocalDate.now(relogio.withZone(FUSO));
        return new DiaSaoPaulo(hoje.atStartOfDay(FUSO).toInstant(), hoje.plusDays(1).atStartOfDay(FUSO).toInstant());
    }

    Instant inicio() { return inicio; }
    Instant fim() { return fim; }
}
