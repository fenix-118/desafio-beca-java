package com.beca.transacoes.application.port.in;

import com.beca.transacoes.domain.model.ResumoDespesas;

import java.time.LocalDate;

public interface ConsultarDespesasUseCase {

    /** Resumo das despesas (saídas aprovadas) do usuário no período. */
    ResumoDoPeriodo consultar(ConsultarDespesasQuery consulta);

    /**
     * Sem inicio e sem fim, o período é o mês atual em São Paulo. Informar só um dos dois é inválido.
     *
     * @param inicio primeiro dia incluído (aaaa-mm-dd, fuso de São Paulo)
     * @param fim    último dia incluído
     */
    record ConsultarDespesasQuery(Long usuarioId, LocalDate inicio, LocalDate fim) {
    }

    /** O resumo junto com o período usado, para o cliente saber qual intervalo foi somado. */
    record ResumoDoPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim, ResumoDespesas resumo) {
    }
}
