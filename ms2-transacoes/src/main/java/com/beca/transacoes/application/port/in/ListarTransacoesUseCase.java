package com.beca.transacoes.application.port.in;

import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;

import java.time.LocalDate;
import java.util.List;

public interface ListarTransacoesUseCase {

    /** Transações do usuário, da mais recente para a mais antiga. */
    List<Transacao> listar(ListarTransacoesQuery consulta);

    /**
     * @param inicio primeiro dia incluído (opcional)
     * @param fim    último dia incluído (opcional)
     * @param tipo   filtra por um tipo (opcional; sem ele, todos)
     */
    record ListarTransacoesQuery(Long usuarioId, LocalDate inicio, LocalDate fim, TipoTransacao tipo) {
    }
}
