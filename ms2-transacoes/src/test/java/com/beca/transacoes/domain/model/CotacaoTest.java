package com.beca.transacoes.domain.model;

import com.beca.transacoes.domain.exception.DadosInvalidosException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CotacaoTest {

    private static final LocalDate SEXTA = LocalDate.of(2026, 11, 6);

    @Test
    void converteReaisParaAMoedaComDuasCasas() {
        Cotacao dolar = new Cotacao(MoedaEstrangeira.USD, new BigDecimal("5.5146"), SEXTA);

        // 120 / 5.5146 = 21.7604... → 21.76
        assertThat(dolar.converter(new BigDecimal("120.00"))).isEqualByComparingTo("21.76");
    }

    @Test
    void usaArredondamentoBancario() {
        Cotacao moeda = new Cotacao(MoedaEstrangeira.EUR, new BigDecimal("4"), SEXTA);

        assertThat(moeda.converter(new BigDecimal("0.10"))).isEqualByComparingTo("0.02"); // 0.025 → 0.02
        assertThat(moeda.converter(new BigDecimal("0.14"))).isEqualByComparingTo("0.04"); // 0.035 → 0.04
    }

    @Test
    void recusaTaxaZeroNegativaOuSemData() {
        assertThatThrownBy(() -> new Cotacao(MoedaEstrangeira.USD, BigDecimal.ZERO, SEXTA))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> new Cotacao(MoedaEstrangeira.USD, new BigDecimal("-1"), SEXTA))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> new Cotacao(MoedaEstrangeira.USD, BigDecimal.ONE, null))
                .isInstanceOf(DadosInvalidosException.class);
    }
}
