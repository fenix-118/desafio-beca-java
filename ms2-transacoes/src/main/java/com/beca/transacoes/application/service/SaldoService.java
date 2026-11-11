package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.ConsultarSaldoUseCase;
import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.domain.exception.ContaNaoEncontradaException;
import com.beca.transacoes.domain.exception.UsuarioNaoEncontradoException;
import com.beca.transacoes.domain.model.SituacaoConta;

import java.math.BigDecimal;
import java.time.Clock;

public class SaldoService implements ConsultarSaldoUseCase {

    private final UsuarioPort usuarios;
    private final ContaPort conta;
    private final TransacaoRepositoryPort repositorio;
    private final Clock relogio;

    public SaldoService(UsuarioPort usuarios, ContaPort conta, TransacaoRepositoryPort repositorio, Clock relogio) {
        this.usuarios = usuarios;
        this.conta = conta;
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    /**
     * @throws UsuarioNaoEncontradoException se o usuário não existe no MS1
     * @throws ContaNaoEncontradaException   se o usuário não tem conta na API Mock
     * @throws com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException se a Mock não responder
     */
    @Override
    public SaldoDaConta consultar(Long usuarioId) {
        if (!usuarios.existe(usuarioId)) {
            throw new UsuarioNaoEncontradoException(usuarioId);
        }
        SituacaoConta situacao = conta.consultar(usuarioId)
                .orElseThrow(() -> new ContaNaoEncontradaException(usuarioId));

        DiaSaoPaulo hoje = DiaSaoPaulo.hoje(relogio);
        BigDecimal gastoHoje = repositorio.somarSaidasAprovadas(usuarioId, hoje.inicio(), hoje.fim());
        BigDecimal disponivel = situacao.limiteDiario().subtract(gastoHoje).max(BigDecimal.ZERO);

        return new SaldoDaConta(usuarioId, situacao.saldo(), situacao.limitePorTransacao(),
                situacao.limiteDiario(), gastoHoje, disponivel, relogio.instant());
    }
}
