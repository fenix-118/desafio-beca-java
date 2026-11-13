package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.model.MensagensTransacao;
import com.beca.transacoes.domain.model.ResumoDespesas;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Análise de despesas (pedido 5): soma as saídas aprovadas do usuário no período. */
public class DespesaService implements ConsultarDespesasUseCase {

    static final String PERIODO_INCOMPLETO =
            "Informe 'inicio' e 'fim' juntos, ou nenhum dos dois para consultar o mês atual.";
    static final String PERIODO_INVALIDO = "A data 'inicio' deve ser anterior ou igual à data 'fim'.";

    /** Um ano: evita carregar vários anos de transações na memória de uma vez. */
    static final int PERIODO_MAXIMO_DIAS = 366;
    static final String PERIODO_LONGO_DEMAIS =
            "O período pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.";

    /** Só saídas podem ser despesa; o filtro de status (aprovada) fica no domínio. */
    private static final Set<TipoTransacao> SAIDAS =
            EnumSet.of(TipoTransacao.RETIRADA, TipoTransacao.TRANSFERENCIA, TipoTransacao.COMPRA);

    private final TransacaoRepositoryPort repositorio;
    private final Clock relogio;

    public DespesaService(TransacaoRepositoryPort repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    @Override
    public ResumoDoPeriodo consultar(ConsultarDespesasQuery consulta) {
        if (consulta.usuarioId() == null || consulta.usuarioId() <= 0) {
            throw new DadosInvalidosException(MensagensTransacao.USUARIO_OBRIGATORIO);
        }
        LocalDate inicio = consulta.inicio();
        LocalDate fim = consulta.fim();
        if (inicio == null && fim == null) {
            // Período padrão: o mês atual em São Paulo, do dia 1º ao último dia (tarefa 5.4).
            YearMonth mesAtual = YearMonth.now(relogio.withZone(DiaSaoPaulo.FUSO));
            inicio = mesAtual.atDay(1);
            fim = mesAtual.atEndOfMonth();
        } else if (inicio == null || fim == null) {
            throw new DadosInvalidosException(PERIODO_INCOMPLETO);
        }
        if (inicio.isAfter(fim)) {
            throw new DadosInvalidosException(PERIODO_INVALIDO);
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new DadosInvalidosException(PERIODO_LONGO_DEMAIS);
        }

        // Datas são dias em São Paulo; o fim é incluído, por isso o limite é o início do dia seguinte.
        Instant de = inicio.atStartOfDay(DiaSaoPaulo.FUSO).toInstant();
        Instant ate = fim.plusDays(1).atStartOfDay(DiaSaoPaulo.FUSO).toInstant();
        List<Transacao> transacoes = repositorio.listar(consulta.usuarioId(), de, ate, SAIDAS);

        return new ResumoDoPeriodo(consulta.usuarioId(), inicio, fim,
                ResumoDespesas.calcular(transacoes, DiaSaoPaulo.FUSO));
    }
}
