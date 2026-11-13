package com.beca.transacoes.infrastructure.adapter.out.persistence;

import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.model.Cotacao;
import com.beca.transacoes.domain.model.MoedaEstrangeira;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class TransacaoPersistenceAdapter implements TransacaoRepositoryPort {

    static final List<TipoTransacao> TIPOS_DE_SAIDA = Arrays.stream(TipoTransacao.values())
            .filter(TipoTransacao::isSaida)
            .toList();

    /**
     * Limites usados quando o período não é informado. Evitam parâmetros nulos na consulta,
     * que o PostgreSQL não consegue tipar.
     */
    static final Instant SEM_INICIO = Instant.parse("1970-01-01T00:00:00Z");
    static final Instant SEM_FIM = Instant.parse("9999-12-31T00:00:00Z");

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

    @Override
    public List<Transacao> listar(Long usuarioId, Instant inicio, Instant fim, Set<TipoTransacao> tipos) {
        return jpa.listar(usuarioId, tipos,
                        inicio == null ? SEM_INICIO : inicio,
                        fim == null ? SEM_FIM : fim)
                .stream()
                .map(TransacaoPersistenceAdapter::paraDominio)
                .toList();
    }

    static TransacaoEntity paraEntidade(Transacao t) {
        Optional<Cotacao> usd = t.cotacao(MoedaEstrangeira.USD);
        Optional<Cotacao> eur = t.cotacao(MoedaEstrangeira.EUR);
        return new TransacaoEntity(t.getId(), t.getUsuarioId(), t.getTipo(), t.getValor(), t.getCategoria(),
                t.getUsuarioDestinoId(), t.getDescricao(), t.getStatus(), t.getMotivoRejeicao(), t.getCriadaEm())
                .comCambio(usd.map(Cotacao::taxa).orElse(null), usd.map(Cotacao::data).orElse(null),
                        eur.map(Cotacao::taxa).orElse(null), eur.map(Cotacao::data).orElse(null));
    }

    static Transacao paraDominio(TransacaoEntity e) {
        List<Cotacao> cotacoes = new ArrayList<>();
        if (e.getCotacaoUsd() != null && e.getDataCotacaoUsd() != null) {
            cotacoes.add(new Cotacao(MoedaEstrangeira.USD, e.getCotacaoUsd(), e.getDataCotacaoUsd()));
        }
        if (e.getCotacaoEur() != null && e.getDataCotacaoEur() != null) {
            cotacoes.add(new Cotacao(MoedaEstrangeira.EUR, e.getCotacaoEur(), e.getDataCotacaoEur()));
        }
        return Transacao.reconstituir(e.getId(), e.getUsuarioId(), e.getTipo(), e.getValor(), e.getCategoria(),
                e.getUsuarioDestinoId(), e.getDescricao(), e.getCriadaEm(), e.getStatus(), e.getMotivoRejeicao(),
                cotacoes);
    }
}
