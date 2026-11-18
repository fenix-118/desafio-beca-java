package com.beca.transacoes.application.port.in;

import com.beca.transacoes.domain.model.Transacao;

import java.time.LocalDate;
import java.util.List;

/** Relatório de transações do usuário no período (pedido 7). */
public interface GerarRelatorioUseCase {

    /** Todas as transações do período (aprovadas e rejeitadas), da mais antiga para a mais recente. */
    RelatorioDoPeriodo gerar(GerarRelatorioQuery consulta);

    /**
     * @param inicio primeiro dia incluído; sem ele e sem {@code fim}, o mês atual em São Paulo
     * @param fim    último dia incluído
     */
    record GerarRelatorioQuery(Long usuarioId, LocalDate inicio, LocalDate fim) {
    }

    /** O período já resolvido (com o mês atual, quando não foi informado) e as transações dele. */
    record RelatorioDoPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim, List<Transacao> transacoes) {
        public RelatorioDoPeriodo {
            transacoes = List.copyOf(transacoes);
        }
    }
}
