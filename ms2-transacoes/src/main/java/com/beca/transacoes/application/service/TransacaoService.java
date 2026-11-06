package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.BuscarTransacaoUseCase;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.model.Transacao;

public class TransacaoService implements RegistrarTransacaoUseCase, BuscarTransacaoUseCase {

    private final TransacaoRepositoryPort repositorio;

    public TransacaoService(TransacaoRepositoryPort repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Transacao registrar(RegistrarTransacaoCommand comando) {
        Transacao transacao = Transacao.solicitar(comando.usuarioId(), comando.tipo(), comando.valor(),
                comando.categoria(), comando.usuarioDestinoId(), comando.descricao());

        if (transacao.precisaDeSaldo()) {
            // Saídas dependem do saldo da conta e entram na tarefa 1.4.
            throw new DadosInvalidosException("O tipo " + transacao.getTipo() + " ainda não está disponível.");
        }
        transacao.avaliar(null, null);

        return repositorio.salvar(transacao);
    }

    @Override
    public Transacao buscarPorId(Long id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new TransacaoNaoEncontradaException(id));
    }
}
