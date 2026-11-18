package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.model.MensagensTransacao;
import com.beca.transacoes.domain.model.ResumoDespesas;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Análise de despesas (pedido 5): soma as saídas aprovadas do usuário no período. */
public class DespesaService implements ConsultarDespesasUseCase {

    // Mantidas aqui para os testes e para quem lê o serviço; as regras ficam em PeriodoSaoPaulo.
    static final String PERIODO_INCOMPLETO = PeriodoSaoPaulo.PERIODO_INCOMPLETO;
    static final String PERIODO_INVALIDO = PeriodoSaoPaulo.PERIODO_INVALIDO;
    static final int PERIODO_MAXIMO_DIAS = PeriodoSaoPaulo.PERIODO_MAXIMO_DIAS;
    static final String PERIODO_LONGO_DEMAIS = PeriodoSaoPaulo.PERIODO_LONGO_DEMAIS;

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
        // Período padrão: o mês atual em São Paulo, do dia 1º ao último dia (tarefa 5.4).
        PeriodoSaoPaulo periodo = PeriodoSaoPaulo.resolver(consulta.inicio(), consulta.fim(), relogio);
        List<Transacao> transacoes = repositorio.listar(consulta.usuarioId(), periodo.de(), periodo.ate(), SAIDAS);

        return new ResumoDoPeriodo(consulta.usuarioId(), periodo.inicio(), periodo.fim(),
                ResumoDespesas.calcular(transacoes, DiaSaoPaulo.FUSO));
    }
}
