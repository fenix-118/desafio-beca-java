package com.beca.transacoes.domain.model;

import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransicaoDeStatusInvalidaException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Transação financeira em BRL (moeda base, dúvida 10).
 *
 * Ciclo de vida: solicitar() cria PENDENTE; avaliar() ou rejeitar() decide uma única vez
 * entre APROVADA e REJEITADA. Depois de decidida, a transação nunca muda.
 */
public class Transacao {

    static final int CASAS_DECIMAIS = 2;
    static final int DIGITOS_INTEIROS = 13; // numeric(15,2) no banco
    static final int TAMANHO_MAXIMO_DESCRICAO = 140;

    private final Long id;
    private final Long usuarioId;
    private final TipoTransacao tipo;
    private final BigDecimal valor;
    private final Categoria categoria;
    private final Long usuarioDestinoId;
    private final String descricao;
    private final Instant criadaEm;
    private StatusTransacao status;
    private MotivoRejeicao motivoRejeicao;

    private Transacao(Long id, Long usuarioId, TipoTransacao tipo, BigDecimal valor, Categoria categoria,
                      Long usuarioDestinoId, String descricao, Instant criadaEm,
                      StatusTransacao status, MotivoRejeicao motivoRejeicao) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.valor = valor;
        this.categoria = categoria;
        this.usuarioDestinoId = usuarioDestinoId;
        this.descricao = descricao;
        this.criadaEm = criadaEm;
        this.status = status;
        this.motivoRejeicao = motivoRejeicao;
    }

    /** Cria uma transação nova, validada e PENDENTE de avaliação. */
    public static Transacao solicitar(Long usuarioId, TipoTransacao tipo, BigDecimal valor,
                                      Categoria categoria, Long usuarioDestinoId, String descricao) {
        if (usuarioId == null || usuarioId <= 0) {
            throw new DadosInvalidosException(MensagensTransacao.USUARIO_OBRIGATORIO);
        }
        if (tipo == null) {
            throw new DadosInvalidosException(MensagensTransacao.TIPO_OBRIGATORIO);
        }
        validarCategoria(tipo, categoria);
        validarDestino(tipo, usuarioId, usuarioDestinoId);

        return new Transacao(null, usuarioId, tipo, normalizarValor(valor), categoria, usuarioDestinoId,
                normalizarDescricao(descricao), Instant.now(), StatusTransacao.PENDENTE, null);
    }

    /** Usado pelo adaptador de persistência para remontar uma transação já gravada. */
    public static Transacao reconstituir(Long id, Long usuarioId, TipoTransacao tipo, BigDecimal valor,
                                         Categoria categoria, Long usuarioDestinoId, String descricao,
                                         Instant criadaEm, StatusTransacao status, MotivoRejeicao motivoRejeicao) {
        return new Transacao(id, usuarioId, tipo, valor, categoria, usuarioDestinoId, descricao,
                criadaEm, status, motivoRejeicao);
    }

    /**
     * Diz, sem mudar nada, se a transação passaria nas regras. Depósito sempre passa e não conta no
     * limite diário (dúvida 9). Saídas são conferidas nesta ordem: saldo, limite por transação e limite diário.
     *
     * @param conta            saldo e limites atuais da conta (obrigatório em saídas)
     * @param totalSaidasNoDia soma das saídas já APROVADAS no dia (fuso America/Sao_Paulo)
     * @return o motivo da rejeição, ou vazio se a transação pode ser aprovada
     */
    public Optional<MotivoRejeicao> verificarRegras(SituacaoConta conta, BigDecimal totalSaidasNoDia) {
        if (!tipo.isSaida()) {
            return Optional.empty();
        }
        Objects.requireNonNull(conta, "A situação da conta é obrigatória para avaliar uma saída.");

        BigDecimal jaGastoHoje = totalSaidasNoDia == null ? BigDecimal.ZERO : totalSaidasNoDia;

        if (valor.compareTo(conta.saldo()) > 0) {
            return Optional.of(MotivoRejeicao.SALDO_INSUFICIENTE);
        }
        if (valor.compareTo(conta.limitePorTransacao()) > 0) {
            return Optional.of(MotivoRejeicao.LIMITE_POR_TRANSACAO_EXCEDIDO);
        }
        if (jaGastoHoje.add(valor).compareTo(conta.limiteDiario()) > 0) {
            return Optional.of(MotivoRejeicao.LIMITE_DIARIO_EXCEDIDO);
        }
        return Optional.empty();
    }

    /** Aplica as regras e decide: APROVADA ou REJEITADA com o motivo. Só pode ser chamado uma vez. */
    public void avaliar(SituacaoConta conta, BigDecimal totalSaidasNoDia) {
        exigirPendente();
        verificarRegras(conta, totalSaidasNoDia)
                .ifPresentOrElse(this::marcarRejeitada, () -> this.status = StatusTransacao.APROVADA);
    }

    /** Saldo da conta depois desta transação: depósito soma, saída subtrai. */
    public BigDecimal saldoApos(SituacaoConta conta) {
        Objects.requireNonNull(conta, "A situação da conta é obrigatória para calcular o saldo.");
        return tipo.isSaida() ? conta.saldo().subtract(valor) : conta.saldo().add(valor);
    }

    /** Rejeita sem avaliar saldo: API Mock fora do ar (dúvida 2) ou destino inexistente (dúvida 8). */
    public void rejeitar(MotivoRejeicao motivo) {
        exigirPendente();
        if (motivo == null) {
            throw new DadosInvalidosException("O motivo da rejeição é obrigatório.");
        }
        marcarRejeitada(motivo);
    }

    /** Saídas dependem do saldo da conta; depósitos não. */
    public boolean precisaDeSaldo() {
        return tipo.isSaida();
    }

    /** Entra na análise de despesas: só saídas aprovadas (tarefa 5.1). */
    public boolean isDespesa() {
        return status == StatusTransacao.APROVADA && tipo.isSaida();
    }

    // ---------- regras internas ----------

    private void marcarRejeitada(MotivoRejeicao motivo) {
        this.status = StatusTransacao.REJEITADA;
        this.motivoRejeicao = motivo;
    }

    private void exigirPendente() {
        if (status != StatusTransacao.PENDENTE) {
            throw new TransicaoDeStatusInvalidaException(
                    "A transação já foi avaliada e está " + status + "; ela não pode mudar.");
        }
    }

    private static BigDecimal normalizarValor(BigDecimal valor) {
        if (valor == null) {
            throw new DadosInvalidosException(MensagensTransacao.VALOR_OBRIGATORIO);
        }
        if (valor.signum() <= 0) {
            throw new DadosInvalidosException(MensagensTransacao.VALOR_POSITIVO);
        }
        if (valor.stripTrailingZeros().scale() > CASAS_DECIMAIS) {
            throw new DadosInvalidosException(MensagensTransacao.VALOR_FORMATO); // recusa em vez de arredondar
        }
        BigDecimal emDuasCasas = valor.setScale(CASAS_DECIMAIS); // exato: só completa os zeros
        if (emDuasCasas.precision() - CASAS_DECIMAIS > DIGITOS_INTEIROS) {
            throw new DadosInvalidosException(MensagensTransacao.VALOR_FORMATO);
        }
        return emDuasCasas;
    }

    private static void validarCategoria(TipoTransacao tipo, Categoria categoria) {
        if (tipo.isSaida() && categoria == null) {
            throw new DadosInvalidosException("O campo 'categoria' é obrigatório para " + tipo + ".");
        }
        if (!tipo.isSaida() && categoria != null) {
            throw new DadosInvalidosException("O campo 'categoria' não se aplica a " + tipo + ".");
        }
    }

    private static void validarDestino(TipoTransacao tipo, Long usuarioId, Long usuarioDestinoId) {
        if (tipo == TipoTransacao.TRANSFERENCIA) {
            if (usuarioDestinoId == null) {
                throw new DadosInvalidosException("O campo 'usuarioDestinoId' é obrigatório para TRANSFERENCIA.");
            }
            if (usuarioDestinoId <= 0) {
                throw new DadosInvalidosException(MensagensTransacao.DESTINO_INVALIDO);
            }
            if (usuarioDestinoId.equals(usuarioId)) {
                throw new DadosInvalidosException("Não é possível transferir para o próprio usuário.");
            }
        } else if (usuarioDestinoId != null) {
            throw new DadosInvalidosException("O campo 'usuarioDestinoId' só se aplica a TRANSFERENCIA.");
        }
    }

    private static String normalizarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        String limpa = descricao.trim();
        if (limpa.length() > TAMANHO_MAXIMO_DESCRICAO) {
            throw new DadosInvalidosException(MensagensTransacao.DESCRICAO_TAMANHO);
        }
        return limpa;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public TipoTransacao getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public Categoria getCategoria() { return categoria; }
    public Long getUsuarioDestinoId() { return usuarioDestinoId; }
    public String getDescricao() { return descricao; }
    public Instant getCriadaEm() { return criadaEm; }
    public StatusTransacao getStatus() { return status; }
    public MotivoRejeicao getMotivoRejeicao() { return motivoRejeicao; }
}
