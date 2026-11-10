package com.beca.transacoes.application.port.out;

import com.beca.transacoes.domain.model.Cotacao;
import com.beca.transacoes.domain.model.MoedaEstrangeira;

import java.time.LocalDate;
import java.util.Optional;

/** Fonte das cotações de câmbio (a BrasilAPI). O câmbio é informativo: nunca derruba uma transação. */
public interface CambioPort {

    /**
     * @param dataDaTransacao dia da transação (fuso de São Paulo)
     * @return a cotação mais recente publicada até esse dia, ou vazio se não for possível obtê-la
     */
    Optional<Cotacao> cotacao(MoedaEstrangeira moeda, LocalDate dataDaTransacao);
}
