package com.beca.transacoes.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;

public interface ConsultarSaldoUseCase {

    /** Saldo e limites lidos da API Mock no momento da chamada (sem cache), mais o gasto de hoje. */
    SaldoDaConta consultar(Long usuarioId);

    /**
     * @param gastoHoje               soma das saídas aprovadas hoje (dia de São Paulo)
     * @param limiteDiarioDisponivel  quanto ainda pode sair hoje; nunca negativo
     */
    record SaldoDaConta(Long usuarioId, BigDecimal saldo, BigDecimal limitePorTransacao, BigDecimal limiteDiario,
                        BigDecimal gastoHoje, BigDecimal limiteDiarioDisponivel, Instant consultadoEm) {
    }
}
