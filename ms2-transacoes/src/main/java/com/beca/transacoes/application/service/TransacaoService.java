package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.BuscarTransacaoUseCase;
import com.beca.transacoes.application.port.in.ListarTransacoesUseCase;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase;
import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.exception.UsuarioNaoEncontradoException;
import com.beca.transacoes.domain.model.MensagensTransacao;
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
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final TransacaoRepositoryPort repositorio;
    private final ContaPort conta;
    private final UsuarioPort usuarios;
    private final Clock relogio;

    public TransacaoService(TransacaoRepositoryPort repositorio, ContaPort conta, UsuarioPort usuarios,
                            Clock relogio) {
        this.repositorio = repositorio;
        this.conta = conta;
        this.usuarios = usuarios;
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
        if (problemaNoDestino.isPresent()) {
            // O dono já foi confirmado: a tentativa fica registrada, rejeitada com o motivo.
            transacao.rejeitar(problemaNoDestino.get());
        } else {
            avaliarComAConta(transacao);
        }
        return repositorio.salvar(transacao);
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
    private void avaliarComAConta(Transacao transacao) {
        Long usuarioId = transacao.getUsuarioId();

        Optional<SituacaoConta> encontrada;
        try {
            encontrada = conta.consultar(usuarioId);
        } catch (ServicoSaldoIndisponivelException e) {
            // Dúvida 2: sem saldo confirmado, nunca aprova às cegas. A rejeição é gravada com o motivo.
            transacao.rejeitar(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
            return;
        }
        if (encontrada.isEmpty()) {
            transacao.rejeitar(MotivoRejeicao.CONTA_INEXISTENTE);
            return;
        }
        SituacaoConta situacao = encontrada.get();

        BigDecimal jaGastoHoje = transacao.precisaDeSaldo() ? saidasAprovadasHoje(usuarioId) : BigDecimal.ZERO;

        Optional<MotivoRejeicao> motivo = transacao.verificarRegras(situacao, jaGastoHoje);
        if (motivo.isPresent()) {
            transacao.rejeitar(motivo.get());
            return;
        }

        try {
            conta.atualizarSaldo(usuarioId, transacao.saldoApos(situacao));
        } catch (ServicoSaldoIndisponivelException e) {
            transacao.rejeitar(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
            return;
        }
        transacao.avaliar(situacao, jaGastoHoje);
    }

    private BigDecimal saidasAprovadasHoje(Long usuarioId) {
        LocalDate hoje = LocalDate.now(relogio.withZone(FUSO));
        Instant inicioDoDia = hoje.atStartOfDay(FUSO).toInstant();
        Instant inicioDeAmanha = hoje.plusDays(1).atStartOfDay(FUSO).toInstant();
        return repositorio.somarSaidasAprovadas(usuarioId, inicioDoDia, inicioDeAmanha);
    }
}
