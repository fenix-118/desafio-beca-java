package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.GerarRelatorioUseCase;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.model.MensagensTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Relatório de transações (pedido 7): mesmas regras de período da análise de despesas. */
public class RelatorioService implements GerarRelatorioUseCase {

    private final TransacaoRepositoryPort repositorio;
    private final Clock relogio;

    public RelatorioService(TransacaoRepositoryPort repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    @Override
    public RelatorioDoPeriodo gerar(GerarRelatorioQuery consulta) {
        if (consulta.usuarioId() == null || consulta.usuarioId() <= 0) {
            throw new DadosInvalidosException(MensagensTransacao.USUARIO_OBRIGATORIO);
        }
        PeriodoSaoPaulo periodo = PeriodoSaoPaulo.resolver(consulta.inicio(), consulta.fim(), relogio);

        // O repositório devolve da mais recente para a mais antiga; um extrato se lê em ordem cronológica.
        List<Transacao> transacoes = new ArrayList<>(repositorio.listar(consulta.usuarioId(),
                periodo.de(), periodo.ate(), EnumSet.allOf(TipoTransacao.class)));
        Collections.reverse(transacoes);

        return new RelatorioDoPeriodo(consulta.usuarioId(), periodo.inicio(), periodo.fim(), transacoes);
    }
}
