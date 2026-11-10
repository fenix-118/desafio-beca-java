package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.BuscarTransacaoUseCase;
import com.beca.transacoes.application.port.in.ListarTransacoesUseCase;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase;
import com.beca.transacoes.application.port.out.CambioPort;
import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.exception.UsuarioNaoEncontradoException;
import com.beca.transacoes.domain.model.MensagensTransacao;
import com.beca.transacoes.domain.model.MoedaEstrangeira;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.SituacaoConta;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TransacaoService
        implements RegistrarTransacaoUseCase, BuscarTransacaoUseCase, ListarTransacoesUseCase {

    /** O "dia" do limite diário segue o dia do usuário (dúvida 11 do refinamento). */
    static final ZoneId FUSO = DiaSaoPaulo.FUSO;

    /** Logger do próprio Java: a camada de aplicação continua sem dependência de framework. */
    private static final System.Logger LOG = System.getLogger(TransacaoService.class.getName());

    private final TransacaoRepositoryPort repositorio;
    private final ContaPort conta;
    private final UsuarioPort usuarios;
    private final CambioPort cambio;
    private final Clock relogio;

    public TransacaoService(TransacaoRepositoryPort repositorio, ContaPort conta, UsuarioPort usuarios,
                            CambioPort cambio, Clock relogio) {
        this.repositorio = repositorio;
        this.conta = conta;
        this.usuarios = usuarios;
        this.cambio = cambio;
        this.relogio = relogio;
    }

    @Override
    public Transacao registrar(RegistrarTransacaoCommand comando) {
        Transacao transacao = Transacao.solicitar(comando.usuarioId(), comando.tipo(), comando.valor(),
                comando.categoria(), comando.usuarioDestinoId(), comando.descricao());

        // Sem dono confirmado no MS1 não existe transação válida para gravar: MS1 fora do ar aqui vira 503.
        if (!usuarios.existe(transacao.getUsuarioId())) {
            throw new UsuarioNaoEncontradoException(transacao.getUsuarioId());
        }

        Optional<MotivoRejeicao> problemaNoDestino = conferirDestino(transacao);
        Optional<SaldoAlterado> saldoAlterado = Optional.empty();
        if (problemaNoDestino.isPresent()) {
            // O dono já foi confirmado: a tentativa fica registrada, rejeitada com o motivo.
            transacao.rejeitar(problemaNoDestino.get());
        } else {
            saldoAlterado = avaliarComAConta(transacao);
        }
        anexarCambio(transacao);
        return gravar(transacao, saldoAlterado);
    }

    /** O que mudou na Mock antes de gravar, para poder desfazer se a gravação falhar. */
    private record SaldoAlterado(Long usuarioId, BigDecimal anterior, BigDecimal novo) {
    }

    /**
     * O PUT na Mock acontece antes de gravar no banco (só aprova com o saldo de fato atualizado).
     * Se a gravação falhar depois disso, o saldo já mudou sem registro: registra um erro com a conta
     * e tenta voltar o saldo anterior. Sem transação distribuída, é o melhor esforço possível.
     */
    private Transacao gravar(Transacao transacao, Optional<SaldoAlterado> saldoAlterado) {
        try {
            return repositorio.salvar(transacao);
        } catch (RuntimeException falhaAoGravar) {
            saldoAlterado.ifPresent(alteracao -> desfazerSaldo(alteracao, transacao, falhaAoGravar));
            throw falhaAoGravar;
        }
    }

    private void desfazerSaldo(SaldoAlterado alteracao, Transacao transacao, RuntimeException falhaAoGravar) {
        LOG.log(System.Logger.Level.ERROR,
                "Falha ao gravar a {0} do usuário {1} depois de atualizar o saldo na Mock ({2} -> {3}); "
                        + "voltando o saldo anterior ({4})",
                transacao.getTipo(), String.valueOf(alteracao.usuarioId()), alteracao.anterior().toPlainString(),
                alteracao.novo().toPlainString(), causaRaiz(falhaAoGravar));
        try {
            conta.atualizarSaldo(alteracao.usuarioId(), alteracao.anterior());
            LOG.log(System.Logger.Level.WARNING, "Saldo do usuário {0} voltou para {1}",
                    String.valueOf(alteracao.usuarioId()), alteracao.anterior().toPlainString());
        } catch (RuntimeException falhaAoDesfazer) {
            LOG.log(System.Logger.Level.ERROR,
                    "AJUSTE MANUAL: não foi possível voltar o saldo do usuário {0} para {1} ({2})",
                    String.valueOf(alteracao.usuarioId()), alteracao.anterior().toPlainString(),
                    causaRaiz(falhaAoDesfazer));
        }
    }

    @Override
    public Transacao buscarPorId(Long id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new TransacaoNaoEncontradaException(id));
    }

    @Override
    public List<Transacao> listar(ListarTransacoesQuery consulta) {
        if (consulta.usuarioId() == null || consulta.usuarioId() <= 0) {
            throw new DadosInvalidosException(MensagensTransacao.USUARIO_OBRIGATORIO);
        }
        if (consulta.inicio() != null && consulta.fim() != null && consulta.inicio().isAfter(consulta.fim())) {
            throw new DadosInvalidosException("A data 'inicio' deve ser anterior ou igual à data 'fim'.");
        }

        // Datas são dias em São Paulo; o fim é incluído, por isso o limite é o início do dia seguinte.
        Instant inicio = consulta.inicio() == null ? null : consulta.inicio().atStartOfDay(FUSO).toInstant();
        Instant fim = consulta.fim() == null ? null : consulta.fim().plusDays(1).atStartOfDay(FUSO).toInstant();
        Set<TipoTransacao> tipos = consulta.tipo() == null
                ? EnumSet.allOf(TipoTransacao.class)
                : EnumSet.of(consulta.tipo());

        return repositorio.listar(consulta.usuarioId(), inicio, fim, tipos);
    }

    /**
     * Guarda o câmbio do dia da transação em cada moeda, aprovada ou rejeitada. É informativo (dúvida 3):
     * se a BrasilAPI não responder, a moeda fica sem cotação e a consulta mostra a conversão como indisponível.
     */
    private void anexarCambio(Transacao transacao) {
        LocalDate diaDaTransacao = LocalDate.ofInstant(transacao.getCriadaEm(), FUSO);
        for (MoedaEstrangeira moeda : MoedaEstrangeira.values()) {
            cambio.cotacao(moeda, diaDaTransacao).ifPresent(transacao::anexarCotacao);
        }
    }

    /**
     * Confere o destino de uma transferência no MS1. Mesma regra do saldo (dúvida 2): com o MS1 fora do ar,
     * a transação não é aprovada às cegas; fica REJEITADA com motivo e é gravada.
     */
    private Optional<MotivoRejeicao> conferirDestino(Transacao transacao) {
        Long destino = transacao.getUsuarioDestinoId();
        if (destino == null) {
            return Optional.empty();
        }
        try {
            return usuarios.existe(destino) ? Optional.empty() : Optional.of(MotivoRejeicao.DESTINO_INEXISTENTE);
        } catch (ServicoUsuariosIndisponivelException e) {
            return Optional.of(MotivoRejeicao.SERVICO_USUARIOS_INDISPONIVEL);
        }
    }

    /**
     * Consulta a conta, aplica as regras e, se passar, grava o novo saldo na Mock ANTES de aprovar.
     * Assim uma transação só fica APROVADA quando o saldo foi de fato atualizado (ponto 2 da revisão).
     */
    private Optional<SaldoAlterado> avaliarComAConta(Transacao transacao) {
        Long usuarioId = transacao.getUsuarioId();

        Optional<SituacaoConta> encontrada;
        try {
            encontrada = conta.consultar(usuarioId);
        } catch (ServicoSaldoIndisponivelException e) {
            // Dúvida 2: sem saldo confirmado, nunca aprova às cegas. A rejeição é gravada com o motivo.
            rejeitarPorMockIndisponivel(transacao, "consultar a conta", e);
            return Optional.empty();
        }
        if (encontrada.isEmpty()) {
            transacao.rejeitar(MotivoRejeicao.CONTA_INEXISTENTE);
            return Optional.empty();
        }
        SituacaoConta situacao = encontrada.get();

        BigDecimal jaGastoHoje = transacao.precisaDeSaldo() ? saidasAprovadasHoje(usuarioId) : BigDecimal.ZERO;

        Optional<MotivoRejeicao> motivo = transacao.verificarRegras(situacao, jaGastoHoje);
        if (motivo.isPresent()) {
            transacao.rejeitar(motivo.get());
            return Optional.empty();
        }

        BigDecimal novoSaldo = transacao.saldoApos(situacao);
        try {
            conta.atualizarSaldo(usuarioId, novoSaldo);
        } catch (ServicoSaldoIndisponivelException e) {
            rejeitarPorMockIndisponivel(transacao, "atualizar o saldo", e);
            return Optional.empty();
        }
        transacao.avaliar(situacao, jaGastoHoje);
        return Optional.of(new SaldoAlterado(usuarioId, situacao.saldo(), novoSaldo));
    }

    private static void rejeitarPorMockIndisponivel(Transacao transacao, String etapa,
                                                    ServicoSaldoIndisponivelException e) {
        // Só a etapa e o tipo do erro: nada de valores nem dados pessoais no log.
        LOG.log(System.Logger.Level.WARNING, "API Mock indisponível ao {0}; transação {1} rejeitada ({2})",
                etapa, transacao.getTipo(), causaRaiz(e));
        transacao.rejeitar(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
    }

    /** Tipo do erro mais interno (ex.: SSLHandshakeException, UnknownHostException), para o diagnóstico. */
    private static String causaRaiz(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz.getClass().getSimpleName();
    }

    private BigDecimal saidasAprovadasHoje(Long usuarioId) {
        DiaSaoPaulo hoje = DiaSaoPaulo.hoje(relogio);
        return repositorio.somarSaidasAprovadas(usuarioId, hoje.inicio(), hoje.fim());
    }
}
