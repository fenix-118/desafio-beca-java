package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.ListarTransacoesUseCase.ListarTransacoesQuery;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase.RegistrarTransacaoCommand;
import com.beca.transacoes.application.port.out.CambioPort;
import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.exception.UsuarioNaoEncontradoException;
import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.Cotacao;
import com.beca.transacoes.domain.model.MoedaEstrangeira;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.SituacaoConta;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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

    @Mock
    private UsuarioPort usuarios;

    /** Sem stub, o Mockito devolve Optional.empty(): a conversão fica indisponível e nada quebra. */
    @Mock
    private CambioPort cambio;

    private TransacaoService service;

    @BeforeEach
    void setUp() {
        service = new TransacaoService(repositorio, conta, usuarios, cambio, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    /** O MS1 confirma que estes usuários existem. */
    private void usuariosExistem(Long... ids) {
        for (Long id : ids) {
            when(usuarios.existe(id)).thenReturn(true);
        }
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
    void depositoSomaAoSaldoNaMockEEAprovadoSemOlharOLimiteDiario() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));

        Transacao t = service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, new BigDecimal("250"), null, null, "Salário"));

        assertThat(gravada().getStatus()).isEqualTo(StatusTransacao.APROVADA);
        assertThat(t.getValor()).isEqualByComparingTo("250.00");
        verify(conta).atualizarSaldo(eq(1L), argThat(saldo -> saldo.compareTo(new BigDecimal("1250.00")) == 0));
        verify(repositorio, never()).somarSaidasAprovadas(anyLong(), any(), any());
    }

    @Test
    void saidaAprovadaDebitaOSaldoNaMockAntesDeGravar() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "120.00"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.APROVADA);
        InOrder ordem = inOrder(conta, repositorio);
        ordem.verify(conta).atualizarSaldo(eq(1L), argThat(saldo -> saldo.compareTo(new BigDecimal("880.00")) == 0));
        ordem.verify(repositorio).salvar(t);
    }

    @Test
    void falhaAoAtualizarOSaldoRejeitaEmVezDeAprovar() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        doThrow(new ServicoSaldoIndisponivelException("timeout no PUT", null))
                .when(conta).atualizarSaldo(eq(1L), any());

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "120.00"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
        assertThat(gravada()).isSameAs(t);
    }

    @Test
    void falhaAoGravarDepoisDoPutVoltaOSaldoAnteriorEPropagaOErro() {
        usuariosExistem(1L);
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repositorio.salvar(any())).thenThrow(new IllegalStateException("banco fora do ar"));

        assertThatThrownBy(() -> service.registrar(saida(TipoTransacao.COMPRA, "120.00")))
                .isInstanceOf(IllegalStateException.class);

        InOrder ordem = inOrder(conta);
        ordem.verify(conta).atualizarSaldo(eq(1L), argThat(s -> s.compareTo(new BigDecimal("880.00")) == 0));
        ordem.verify(conta).atualizarSaldo(eq(1L), argThat(s -> s.compareTo(new BigDecimal("1000.00")) == 0));
    }

    @Test
    void seNaoDerParaDesfazerOErroOriginalContinuaSendoPropagado() {
        usuariosExistem(1L);
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repositorio.salvar(any())).thenThrow(new IllegalStateException("banco fora do ar"));
        doNothing().doThrow(new ServicoSaldoIndisponivelException("Mock caiu", null))
                .when(conta).atualizarSaldo(eq(1L), any());

        assertThatThrownBy(() -> service.registrar(saida(TipoTransacao.COMPRA, "120.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("banco fora do ar");
    }

    @Test
    void falhaAoGravarUmaRejeitadaNaoMexeNoSaldo() {
        usuariosExistem(1L);
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repositorio.salvar(any())).thenThrow(new IllegalStateException("banco fora do ar"));

        assertThatThrownBy(() -> service.registrar(saida(TipoTransacao.COMPRA, "600.00")))
                .isInstanceOf(IllegalStateException.class);
        verify(conta, never()).atualizarSaldo(anyLong(), any());
    }

    @Test
    void depositoComAMockForaDoArTambemERejeitadoEGravado() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenThrow(new ServicoSaldoIndisponivelException("Connection refused", null));

        Transacao t = service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, null));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
        assertThat(t.getMotivoRejeicao().getDescricao()).contains("indisponível");
        assertThat(gravada()).isSameAs(t);
    }

    @Test
    void depositoSemContaNaMockERejeitado() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.empty());

        Transacao t = service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, null));

        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.CONTA_INEXISTENTE);
        verify(conta, never()).atualizarSaldo(anyLong(), any());
    }

    @Test
    void retiradaAcimaDoSaldoERejeitadaComMotivoEFicaGravada() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        Transacao t = service.registrar(saida(TipoTransacao.RETIRADA, "1000.01"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SALDO_INSUFICIENTE);
        assertThat(gravada()).isSameAs(t);
        verify(conta, never()).atualizarSaldo(anyLong(), any());
    }

    @Test
    void osQuatroTiposPodemSerCriados() {
        usuariosExistem(1L, 2L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
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
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(1L, INICIO_DO_DIA_SP, FIM_DO_DIA_SP))
                .thenReturn(new BigDecimal("750.00"));

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "50.01"));

        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.LIMITE_DIARIO_EXCEDIDO);
        verify(repositorio).somarSaidasAprovadas(1L, INICIO_DO_DIA_SP, FIM_DO_DIA_SP);
    }

    @Test
    void compraAcimaDoLimitePorTransacaoERejeitada() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "500.01"));

        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.LIMITE_POR_TRANSACAO_EXCEDIDO);
    }

    @Test
    void servicoDeSaldoForaDoArRejeitaEGravaSemAprovarAsCegas() {
        usuariosExistem(1L);
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
        verifyNoInteractions(conta, repositorio, usuarios);
    }

    @Test
    void compraSemCategoriaERecusada() {
        assertThatThrownBy(() -> service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.COMPRA, BigDecimal.TEN, null, null, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("categoria");
        verifyNoInteractions(conta, repositorio, usuarios);
    }

    @Test
    void buscarInexistenteLancaNaoEncontrada() {
        when(repositorio.buscarPorId(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(42L))
                .isInstanceOf(TransacaoNaoEncontradaException.class)
                .hasMessageContaining("42");
    }

    @Test
    void usuarioSemContaNaMockTemASaidaRejeitadaEGravada() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.empty());

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "10.00"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.CONTA_INEXISTENTE);
        assertThat(gravada()).isSameAs(t);
        verify(repositorio, never()).somarSaidasAprovadas(anyLong(), any(), any());
    }

    // ---------- câmbio (BJ-17) ----------

    @Test
    void guardaACotacaoDeUsdEEurDoDiaDaTransacaoMesmoQuandoRejeitada() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        Cotacao dolar = new Cotacao(MoedaEstrangeira.USD, new BigDecimal("5.5146"), java.time.LocalDate.of(2026, 11, 9));
        Cotacao euro = new Cotacao(MoedaEstrangeira.EUR, new BigDecimal("6.1317"), java.time.LocalDate.of(2026, 11, 6));
        when(cambio.cotacao(eq(MoedaEstrangeira.USD), any())).thenReturn(Optional.of(dolar));
        when(cambio.cotacao(eq(MoedaEstrangeira.EUR), any())).thenReturn(Optional.of(euro));

        Transacao t = service.registrar(saida(TipoTransacao.COMPRA, "600.00")); // acima do limite: rejeitada

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(gravada().getCotacoes()).containsExactlyInAnyOrder(dolar, euro);
        java.time.LocalDate diaEmSp = java.time.LocalDate.ofInstant(t.getCriadaEm(), java.time.ZoneId.of("America/Sao_Paulo"));
        verify(cambio).cotacao(MoedaEstrangeira.USD, diaEmSp);
        verify(cambio).cotacao(MoedaEstrangeira.EUR, diaEmSp);
    }

    @Test
    void semCotacaoATransacaoEGravadaDoMesmoJeito() {
        usuariosExistem(1L);
        salvarDevolveOMesmo();
        when(conta.consultar(1L)).thenReturn(Optional.of(CONTA));

        Transacao t = service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, null));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.APROVADA);
        assertThat(t.getCotacoes()).isEmpty();
        assertThat(gravada()).isSameAs(t);
    }

    // ---------- validação do usuário no MS1 ----------

    @Test
    void donoInexistenteNoMs1Responde404ENadaEGravado() {
        when(usuarios.existe(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, null)))
                .isInstanceOf(UsuarioNaoEncontradoException.class)
                .hasMessageContaining("1");
        verifyNoInteractions(conta, repositorio);
    }

    @Test
    void transferenciaParaDestinoInexistenteFicaRejeitadaEGravadaSemConsultarSaldo() {
        when(usuarios.existe(1L)).thenReturn(true);
        when(usuarios.existe(2L)).thenReturn(false);
        salvarDevolveOMesmo();

        Transacao t = service.registrar(saida(TipoTransacao.TRANSFERENCIA, "10.00"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.DESTINO_INEXISTENTE);
        assertThat(gravada()).isSameAs(t);
        verifyNoInteractions(conta);
    }

    @Test
    void ms1ForaDoArNaoGravaNada() {
        when(usuarios.existe(1L)).thenThrow(new ServicoUsuariosIndisponivelException("timeout", null));

        assertThatThrownBy(() -> service.registrar(saida(TipoTransacao.COMPRA, "10.00")))
                .isInstanceOf(ServicoUsuariosIndisponivelException.class);
        verifyNoInteractions(conta, repositorio);
    }

    @Test
    void ms1ForaDoArAoConferirODestinoRejeitaComMotivoEGrava() {
        when(usuarios.existe(1L)).thenReturn(true);
        when(usuarios.existe(2L)).thenThrow(new ServicoUsuariosIndisponivelException("timeout", null));
        salvarDevolveOMesmo();

        Transacao t = service.registrar(saida(TipoTransacao.TRANSFERENCIA, "10.00"));

        assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SERVICO_USUARIOS_INDISPONIVEL);
        assertThat(gravada()).isSameAs(t);
        verifyNoInteractions(conta);
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
