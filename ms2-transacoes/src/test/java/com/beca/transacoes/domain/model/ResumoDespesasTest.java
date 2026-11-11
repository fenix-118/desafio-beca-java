package com.beca.transacoes.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumoDespesasTest {

    private static final Instant QUANDO = Instant.parse("2026-11-10T15:00:00Z");

    static Transacao aprovada(TipoTransacao tipo, String valor, Categoria categoria) {
        Long destino = tipo == TipoTransacao.TRANSFERENCIA ? 4L : null;
        return Transacao.reconstituir(1L, 3L, tipo, new BigDecimal(valor), categoria, destino, null,
                QUANDO, StatusTransacao.APROVADA, null);
    }

    @Test
    void cincoComprasEmTresCategoriasGeramTresLinhasETotalCorreto() {
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(
                aprovada(TipoTransacao.COMPRA, "50.00", Categoria.ALIMENTACAO),
                aprovada(TipoTransacao.COMPRA, "30.00", Categoria.ALIMENTACAO),
                aprovada(TipoTransacao.COMPRA, "100.00", Categoria.MORADIA),
                aprovada(TipoTransacao.COMPRA, "15.50", Categoria.TRANSPORTE),
                aprovada(TipoTransacao.COMPRA, "4.50", Categoria.TRANSPORTE)));

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
                aprovada(TipoTransacao.TRANSFERENCIA, "60.00", Categoria.OUTROS)));

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
                aprovada(TipoTransacao.COMPRA, "25.00", Categoria.SAUDE)));

        assertThat(resumo.porCategoria()).extracting(ResumoDespesas.TotalPorCategoria::categoria)
                .containsExactly(Categoria.SAUDE);
        assertThat(resumo.totalGeral()).isEqualByComparingTo("25.00");
        assertThat(resumo.quantidade()).isEqualTo(1);
    }

    @Test
    void semDespesasOResumoVemVazioComTotalZero() {
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of());

        assertThat(resumo.porCategoria()).isEmpty();
        assertThat(resumo.totalGeral()).isEqualTo(new BigDecimal("0.00"));
        assertThat(resumo.quantidade()).isZero();
    }
}
