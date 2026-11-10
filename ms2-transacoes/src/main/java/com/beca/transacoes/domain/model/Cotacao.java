package com.beca.transacoes.domain.model;

import com.beca.transacoes.domain.exception.DadosInvalidosException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Quanto custa 1 unidade da moeda em reais (PTAX de venda), na data em que a cotação foi publicada.
 * A data é a da cotação, não a da transação: num domingo, a cotação usada é a de sexta.
 */
public record Cotacao(MoedaEstrangeira moeda, BigDecimal taxa, LocalDate data) {

    static final int CASAS_DO_VALOR_CONVERTIDO = 2;

    public Cotacao {
        if (moeda == null) {
            throw new DadosInvalidosException("A moeda da cotação é obrigatória.");
        }
        if (taxa == null || taxa.signum() <= 0) {
            throw new DadosInvalidosException("A taxa da cotação deve ser maior que zero.");
        }
        if (data == null) {
            throw new DadosInvalidosException("A data da cotação é obrigatória.");
        }
    }

    /** Converte um valor em reais para esta moeda, com 2 casas (arredondamento bancário). */
    public BigDecimal converter(BigDecimal valorEmReais) {
        return valorEmReais.divide(taxa, CASAS_DO_VALOR_CONVERTIDO, RoundingMode.HALF_EVEN);
    }
}
