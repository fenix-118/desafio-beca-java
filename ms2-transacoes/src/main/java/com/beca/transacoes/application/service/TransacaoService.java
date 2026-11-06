package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.BuscarTransacaoUseCase;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase;
import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.SituacaoConta;
import com.beca.transacoes.domain.model.Transacao;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class TransacaoService implements RegistrarTransacaoUseCase, BuscarTransacaoUseCase {

    /** O "dia" do limite diário segue o dia do usuário (dúvida 11 do refinamento). */
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final TransacaoRepositoryPort repositorio;
    private final ContaPort conta;
    private final Clock relogio;

    public TransacaoService(TransacaoRepositoryPort repositorio, ContaPort conta, Clock relogio) {
        this.repositorio = repositorio;
        this.conta = conta;
        this.relogio = relogio;
    }

    @Override
    public Transacao registrar(RegistrarTransacaoCommand comando) {
        Transacao transacao = Transacao.solicitar(comando.usuarioId(), comando.tipo(), comando.valor(),
                comando.categoria(), comando.usuarioDestinoId(), comando.descricao());

        if (transacao.precisaDeSaldo()) {
            avaliarSaida(transacao);
        } else {
            transacao.avaliar(null, null);
        }
        return repositorio.salvar(transacao);
    }

    @Override
    public Transacao buscarPorId(Long id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new TransacaoNaoEncontradaException(id));
    }

    private void avaliarSaida(Transacao transacao) {
        SituacaoConta situacao;
        try {
            situacao = conta.consultar(transacao.getUsuarioId());
        } catch (ServicoSaldoIndisponivelException e) {
            // Dúvida 2: sem saldo confirmado, nunca aprova às cegas. A rejeição é gravada com o motivo.
            transacao.rejeitar(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
            return;
        }

        LocalDate hoje = LocalDate.now(relogio.withZone(FUSO));
        Instant inicioDoDia = hoje.atStartOfDay(FUSO).toInstant();
        Instant inicioDeAmanha = hoje.plusDays(1).atStartOfDay(FUSO).toInstant();
        BigDecimal jaGastoHoje = repositorio.somarSaidasAprovadas(
                transacao.getUsuarioId(), inicioDoDia, inicioDeAmanha);

        transacao.avaliar(situacao, jaGastoHoje);
    }
}
