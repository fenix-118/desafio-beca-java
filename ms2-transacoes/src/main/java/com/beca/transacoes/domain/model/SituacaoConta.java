package com.beca.transacoes.domain.model;

import com.beca.transacoes.domain.exception.DadosInvalidosException;

import java.math.BigDecimal;

/**
 * Foto da conta no momento da transação: saldo e limites vindos da API Mock.
 * O domínio não sabe de onde os dados vêm, só que precisam ser válidos.
 */
public record SituacaoConta(BigDecimal saldo, BigDecimal limitePorTransacao, BigDecimal limiteDiario) {

    public SituacaoConta {
        exigirNaoNegativo(saldo, "saldo");
        exigirNaoNegativo(limitePorTransacao, "limitePorTransacao");
        exigirNaoNegativo(limiteDiario, "limiteDiario");
    }

    private static void exigirNaoNegativo(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) {
            throw new DadosInvalidosException("O campo '" + campo + "' da conta deve ser zero ou positivo.");
        }
    }
}
