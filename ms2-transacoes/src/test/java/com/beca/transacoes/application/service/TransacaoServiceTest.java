package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.ListarTransacoesUseCase.ListarTransacoesQuery;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase.RegistrarTransacaoCommand;
import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.SituacaoConta;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransacaoServiceTest {

    /** 06/11 às 02:30 UTC = 05/11 às 23:30 em São Paulo: o "dia" ainda é 05/11. */
    private static final Instant AGORA = Instant.parse("2026-11-06T02:30:00Z");
    private static final Instant INICIO_DO_DIA_SP = Instant.parse("2026-11-05T03:00:00Z");
    private static final Instant FIM_DO_DIA_SP = Instant.parse("2026-11-06T03:00:00Z");

    /** Saldo 1000, limite por transação 500, limite diário 800. */
    private static final SituacaoConta CONTA = new SituacaoConta(
            new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("800.00"));

    @Mock
    private TransacaoRepositoryPort repositorio;

    @Mock
    private ContaPort conta;

    private TransacaoService service;

    @BeforeEach
    void setUp() {
        service = new TransacaoService(repositorio, conta, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void salvarDevolveOMesmo() {
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RegistrarTransacaoCommand saida(TipoTransacao tipo, String valor) {
        Long destino = tipo == TipoTransacao.TRANSFERENCIA ? 2L : null;
        return new RegistrarTransacaoCommand(1L, tipo, new BigDecimal(valor), Categoria.OUTROS, destino, null);
    }

    private Transacao gravada() {
        ArgumentCaptor<Transacao> captor = ArgumentCaptor.forClass(Transacao.class);
        verify(repositorio).salvar(captor.capture());
        return captor.getValue();
    }

    @Test
    void depositoEAprovadoSemConsultarAConta() {
        salvarDevolveOMesmo();

        Transacao t = service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, new BigDecimal("250"), null, null, "Salário"));

        assertThat(gravada().getStatus()).isEqualTo(StatusTransacao.APROVADA);
        assertThat(t.getValor()).isEqualByComparingTo("250.00");
        verifyNoInteractions(conta);
    }

    @Test
    void retiradaAcimaDoSaldoERejeitadaComMotivoEFicaGravada() {
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(CONTA);
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        Transacao t = service.registrar(saida(TipoTransacao.RETIRADA, "1000.01"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SALDO_INSUFICIENTE);
        assertThat(gravada()).isSameAs(t);
    }

    @Test
    void osQuatroTiposPodemSerCriados() {
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(CONTA);
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        assertThat(service.registrar(saida(TipoTransacao.RETIRADA, "10")).getStatus())
                .isEqualTo(StatusTransacao.APROVADA);
        assertThat(service.registrar(saida(TipoTransacao.TRANSFERENCIA, "10")).getStatus())
                .isEqualTo(StatusTransacao.APROVADA);
        assertThat(service.registrar(saida(TipoTransacao.COMPRA, "10")).getStatus())
                .isEqualTo(StatusTransacao.APROVADA);
        assertThat(service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, null)).getStatus())
                .isEqualTo(StatusTransacao.APROVADA);
    }

    @Test
    void limiteDiarioUsaODiaDeSaoPauloENaoODiaUtc() {
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(CONTA);
        when(repositorio.somarSaidasAprovadas(1L, INICIO_DO_DIA_SP, FIM_DO_DIA_SP))
                .thenReturn(new BigDecimal("750.00"));

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "50.01"));

        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.LIMITE_DIARIO_EXCEDIDO);
        verify(repositorio).somarSaidasAprovadas(1L, INICIO_DO_DIA_SP, FIM_DO_DIA_SP);
    }

    @Test
    void compraAcimaDoLimitePorTransacaoERejeitada() {
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(CONTA);
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "500.01"));

        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.LIMITE_POR_TRANSACAO_EXCEDIDO);
    }

    @Test
    void servicoDeSaldoForaDoArRejeitaEGravaSemAprovarAsCegas() {
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenThrow(new ServicoSaldoIndisponivelException("timeout", null));

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "10.00"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
        assertThat(gravada()).isSameAs(t);
        verify(repositorio, never()).somarSaidasAprovadas(anyLong(), any(), any());
    }

    @Test
    void transferenciaSemDestinoNaoConsultaContaNemGrava() {
        assertThatThrownBy(() -> service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.TRANSFERENCIA, BigDecimal.TEN, Categoria.OUTROS, null, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("usuarioDestinoId");
        verifyNoInteractions(conta, repositorio);
    }

    @Test
    void compraSemCategoriaERecusada() {
        assertThatThrownBy(() -> service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.COMPRA, BigDecimal.TEN, null, null, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("categoria");
        verifyNoInteractions(conta, repositorio);
    }

    @Test
    void buscarInexistenteLancaNaoEncontrada() {
        when(repositorio.buscarPorId(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(42L))
                .isInstanceOf(TransacaoNaoEncontradaException.class)
                .hasMessageContaining("42");
    }

    // ---------- listagem (BJ-6) ----------

    @Test
    void listarConverteOPeriodoParaDiasDeSaoPauloComOFimIncluido() {
        when(repositorio.listar(any(), any(), any(), any())).thenReturn(List.of());

        service.listar(new ListarTransacoesQuery(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                TipoTransacao.COMPRA));

        verify(repositorio).listar(1L,
                Instant.parse("2026-11-01T03:00:00Z"),
                Instant.parse("2026-12-01T03:00:00Z"),
                EnumSet.of(TipoTransacao.COMPRA));
    }

    @Test
    void listarSemFiltrosBuscaTodosOsTiposSemLimiteDePeriodo() {
        when(repositorio.listar(any(), any(), any(), any())).thenReturn(List.of());

        service.listar(new ListarTransacoesQuery(1L, null, null, null));

        verify(repositorio).listar(1L, null, null, EnumSet.allOf(TipoTransacao.class));
    }

    @Test
    void listarComInicioDepoisDoFimERecusado() {
        assertThatThrownBy(() -> service.listar(new ListarTransacoesQuery(
                1L, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("'inicio'");
        verifyNoInteractions(repositorio);
    }

    @Test
    void listarExigeUsuario() {
        assertThatThrownBy(() -> service.listar(new ListarTransacoesQuery(null, null, null, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("usuarioId");
    }
}
