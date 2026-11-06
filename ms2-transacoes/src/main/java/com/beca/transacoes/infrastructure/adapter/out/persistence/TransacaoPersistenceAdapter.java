package com.beca.transacoes.infrastructure.adapter.out.persistence;

import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class TransacaoPersistenceAdapter implements TransacaoRepositoryPort {

    static final List<TipoTransacao> TIPOS_DE_SAIDA = Arrays.stream(TipoTransacao.values())
            .filter(TipoTransacao::isSaida)
            .toList();

    private final TransacaoJpaRepository jpa;

    public TransacaoPersistenceAdapter(TransacaoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Transacao salvar(Transacao transacao) {
        if (transacao.getStatus() == StatusTransacao.PENDENTE) {
            // Proteção contra erro de programação: só transação decidida vai para o banco.
            throw new IllegalStateException("Transação PENDENTE não pode ser gravada; avalie antes de salvar.");
        }
        return paraDominio(jpa.saveAndFlush(paraEntidade(transacao)));
    }

    @Override
    public Optional<Transacao> buscarPorId(Long id) {
        return jpa.findById(id).map(TransacaoPersistenceAdapter::paraDominio);
    }

    @Override
    public BigDecimal somarSaidasAprovadas(Long usuarioId, Instant inicio, Instant fim) {
        BigDecimal soma = jpa.somarValores(usuarioId, StatusTransacao.APROVADA, TIPOS_DE_SAIDA, inicio, fim);
        return soma == null ? BigDecimal.ZERO : soma;
    }

    static TransacaoEntity paraEntidade(Transacao t) {
        return new TransacaoEntity(t.getId(), t.getUsuarioId(), t.getTipo(), t.getValor(), t.getCategoria(),
                t.getUsuarioDestinoId(), t.getDescricao(), t.getStatus(), t.getMotivoRejeicao(), t.getCriadaEm());
    }

    static Transacao paraDominio(TransacaoEntity e) {
        return Transacao.reconstituir(e.getId(), e.getUsuarioId(), e.getTipo(), e.getValor(), e.getCategoria(),
                e.getUsuarioDestinoId(), e.getDescricao(), e.getCriadaEm(), e.getStatus(), e.getMotivoRejeicao());
    }
}
