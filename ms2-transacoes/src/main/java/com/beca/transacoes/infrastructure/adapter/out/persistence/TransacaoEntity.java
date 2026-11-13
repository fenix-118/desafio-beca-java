package com.beca.transacoes.infrastructure.adapter.out.persistence;

import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Tabela do transacoes_db. O usuario_id é só um número: o MS2 não tem chave estrangeira
 * nem acesso às tabelas do MS1, que vive em outro banco.
 */
@Entity
@Table(name = "transacoes",
        indexes = @Index(name = "idx_transacoes_usuario_data", columnList = "usuario_id, criada_em"))
public class TransacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoTransacao tipo;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private Categoria categoria;

    @Column(name = "usuario_destino_id", updatable = false)
    private Long usuarioDestinoId;

    @Column(length = 140, updatable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTransacao status;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rejeicao", length = 40)
    private MotivoRejeicao motivoRejeicao;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    /** Câmbio guardado no registro; nulo quando a BrasilAPI não respondeu. */
    @Column(name = "cotacao_usd", precision = 12, scale = 6, updatable = false)
    private BigDecimal cotacaoUsd;

    @Column(name = "data_cotacao_usd", updatable = false)
    private LocalDate dataCotacaoUsd;

    @Column(name = "cotacao_eur", precision = 12, scale = 6, updatable = false)
    private BigDecimal cotacaoEur;

    @Column(name = "data_cotacao_eur", updatable = false)
    private LocalDate dataCotacaoEur;

    protected TransacaoEntity() {
    }

    public TransacaoEntity(Long id, Long usuarioId, TipoTransacao tipo, BigDecimal valor, Categoria categoria,
                           Long usuarioDestinoId, String descricao, StatusTransacao status,
                           MotivoRejeicao motivoRejeicao, Instant criadaEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.valor = valor;
        this.categoria = categoria;
        this.usuarioDestinoId = usuarioDestinoId;
        this.descricao = descricao;
        this.status = status;
        this.motivoRejeicao = motivoRejeicao;
        this.criadaEm = criadaEm;
    }

    public TransacaoEntity comCambio(BigDecimal cotacaoUsd, LocalDate dataCotacaoUsd,
                                     BigDecimal cotacaoEur, LocalDate dataCotacaoEur) {
        this.cotacaoUsd = cotacaoUsd;
        this.dataCotacaoUsd = dataCotacaoUsd;
        this.cotacaoEur = cotacaoEur;
        this.dataCotacaoEur = dataCotacaoEur;
        return this;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public TipoTransacao getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public Categoria getCategoria() { return categoria; }
    public Long getUsuarioDestinoId() { return usuarioDestinoId; }
    public String getDescricao() { return descricao; }
    public StatusTransacao getStatus() { return status; }
    public MotivoRejeicao getMotivoRejeicao() { return motivoRejeicao; }
    public Instant getCriadaEm() { return criadaEm; }
    public BigDecimal getCotacaoUsd() { return cotacaoUsd; }
    public LocalDate getDataCotacaoUsd() { return dataCotacaoUsd; }
    public BigDecimal getCotacaoEur() { return cotacaoEur; }
    public LocalDate getDataCotacaoEur() { return dataCotacaoEur; }
}
