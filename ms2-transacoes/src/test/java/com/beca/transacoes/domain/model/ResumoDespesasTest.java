package com.beca.transacoes.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumoDespesasTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Instant QUANDO = Instant.parse("2026-11-10T15:00:00Z");

    static Transacao aprovada(TipoTransacao tipo, String valor, Categoria categoria) {
        return aprovadaEm(QUANDO, tipo, valor, categoria);
    }

    static Transacao aprovadaEm(Instant quando, TipoTransacao tipo, String valor, Categoria categoria) {
        Long destino = tipo == TipoTransacao.TRANSFERENCIA ? 4L : null;
        return Transacao.reconstituir(1L, 3L, tipo, new BigDecimal(valor), categoria, destino, null,
                quando, StatusTransacao.APROVADA, null);
    }

    @Test
    void cincoComprasEmTresCategoriasGeramTresLinhasETotalCorreto() {
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(
                aprovada(TipoTransacao.COMPRA, "50.00", Categoria.ALIMENTACAO),
                aprovada(TipoTransacao.COMPRA, "30.00", Categoria.ALIMENTACAO),
                aprovada(TipoTransacao.COMPRA, "100.00", Categoria.MORADIA),
                aprovada(TipoTransacao.COMPRA, "15.50", Categoria.TRANSPORTE),
                aprovada(TipoTransacao.COMPRA, "4.50", Categoria.TRANSPORTE)), SP);

        assertThat(resumo.porCategoria()).containsExactly(
                new ResumoDespesas.TotalPorCategoria(Categoria.MORADIA, new BigDecimal("100.00"), 1),
                new ResumoDespesas.TotalPorCategoria(Categoria.ALIMENTACAO, new BigDecimal("80.00"), 2),
                new ResumoDespesas.TotalPorCategoria(Categoria.TRANSPORTE, new BigDecimal("20.00"), 2));
        assertThat(resumo.totalGeral()).isEqualByComparingTo("200.00");
        assertThat(resumo.quantidade()).isEqualTo(5);
    }

    @Test
    void retiradaETransferenciaEnviadaTambemSaoDespesa() {
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(
                aprovada(TipoTransacao.RETIRADA, "40.00", Categoria.OUTROS),
                aprovada(TipoTransacao.TRANSFERENCIA, "60.00", Categoria.OUTROS)), SP);

        assertThat(resumo.porCategoria()).containsExactly(
                new ResumoDespesas.TotalPorCategoria(Categoria.OUTROS, new BigDecimal("100.00"), 2));
        assertThat(resumo.totalGeral()).isEqualByComparingTo("100.00");
    }

    @Test
    void depositoERejeitadasNaoEntramNoResumo() {
        Transacao deposito = Transacao.reconstituir(2L, 3L, TipoTransacao.DEPOSITO, new BigDecimal("500.00"),
                null, null, null, QUANDO, StatusTransacao.APROVADA, null);
        Transacao rejeitada = Transacao.reconstituir(3L, 3L, TipoTransacao.COMPRA, new BigDecimal("900.00"),
                Categoria.LAZER, null, null, QUANDO, StatusTransacao.REJEITADA, MotivoRejeicao.SALDO_INSUFICIENTE);

        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(deposito, rejeitada,
                aprovada(TipoTransacao.COMPRA, "25.00", Categoria.SAUDE)), SP);

        assertThat(resumo.porCategoria()).extracting(ResumoDespesas.TotalPorCategoria::categoria)
                .containsExactly(Categoria.SAUDE);
        assertThat(resumo.totalGeral()).isEqualByComparingTo("25.00");
        assertThat(resumo.quantidade()).isEqualTo(1);
    }

    @Test
    void semDespesasOResumoVemVazioComTotalZero() {
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(), SP);

        assertThat(resumo.porCategoria()).isEmpty();
        assertThat(resumo.totalGeral()).isEqualTo(new BigDecimal("0.00"));
        assertThat(resumo.quantidade()).isZero();
    }

    @Test
    void totaisPorDiaEPorMesBatemComASomaDasTransacoes() {
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(
                aprovadaEm(Instant.parse("2026-10-31T15:00:00Z"), TipoTransacao.COMPRA, "10.00", Categoria.LAZER),
                aprovadaEm(Instant.parse("2026-11-02T12:00:00Z"), TipoTransacao.COMPRA, "20.00", Categoria.LAZER),
                aprovadaEm(Instant.parse("2026-11-02T22:00:00Z"), TipoTransacao.RETIRADA, "30.00", Categoria.OUTROS),
                aprovadaEm(Instant.parse("2026-11-05T12:00:00Z"), TipoTransacao.COMPRA, "40.00", Categoria.SAUDE)), SP);

        assertThat(resumo.porDia()).containsExactly(
                new ResumoDespesas.TotalPorDia(LocalDate.of(2026, 10, 31), new BigDecimal("10.00"), 1),
                new ResumoDespesas.TotalPorDia(LocalDate.of(2026, 11, 2), new BigDecimal("50.00"), 2),
                new ResumoDespesas.TotalPorDia(LocalDate.of(2026, 11, 5), new BigDecimal("40.00"), 1));
        assertThat(resumo.porMes()).containsExactly(
                new ResumoDespesas.TotalPorMes(YearMonth.of(2026, 10), new BigDecimal("10.00"), 1),
                new ResumoDespesas.TotalPorMes(YearMonth.of(2026, 11), new BigDecimal("90.00"), 3));
        assertThat(resumo.totalGeral()).isEqualByComparingTo("100.00");
    }

    @Test
    void oDiaEOMesSaoOsDeSaoPauloENaoOsDeUtc() {
        // 01/11 às 01:30 UTC ainda é 31/10 às 22:30 em São Paulo.
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(
                aprovadaEm(Instant.parse("2026-11-01T01:30:00Z"), TipoTransacao.COMPRA, "15.00", Categoria.LAZER)), SP);

        assertThat(resumo.porDia()).extracting(ResumoDespesas.TotalPorDia::dia)
                .containsExactly(LocalDate.of(2026, 10, 31));
        assertThat(resumo.porMes()).extracting(ResumoDespesas.TotalPorMes::mes)
                .containsExactly(YearMonth.of(2026, 10));
    }
}
