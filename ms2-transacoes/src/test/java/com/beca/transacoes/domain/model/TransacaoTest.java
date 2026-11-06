package com.beca.transacoes.domain.model;

import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransicaoDeStatusInvalidaException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransacaoTest {

    private static final Long USUARIO = 1L;
    private static final Long OUTRO_USUARIO = 2L;

    /** Saldo 1000, limite por transação 500, limite diário 800. */
    private static final SituacaoConta CONTA = new SituacaoConta(
            new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("800.00"));

    private static Transacao compra(String valor) {
        return Transacao.solicitar(USUARIO, TipoTransacao.COMPRA, new BigDecimal(valor),
                Categoria.ALIMENTACAO, null, "Mercado");
    }

    private static Transacao deposito(String valor) {
        return Transacao.solicitar(USUARIO, TipoTransacao.DEPOSITO, new BigDecimal(valor), null, null, null);
    }

    // ---------- criação ----------

    @Nested
    class Solicitar {

        @Test
        void deveNascerPendenteComValorEmDuasCasas() {
            Transacao t = compra("10.5");

            assertThat(t.getStatus()).isEqualTo(StatusTransacao.PENDENTE);
            assertThat(t.getValor()).isEqualByComparingTo("10.50");
            assertThat(t.getValor().scale()).isEqualTo(2);
            assertThat(t.getMotivoRejeicao()).isNull();
            assertThat(t.getCriadaEm()).isNotNull();
        }

        @Test
        void deveAceitarZerosAlemDaSegundaCasa() {
            assertThat(compra("10.500").getValor()).isEqualByComparingTo("10.50");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-1.00"})
        void deveRecusarValorZeroOuNegativo(String valor) {
            assertThatThrownBy(() -> compra(valor))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("maior que zero");
        }

        @Test
        void deveRecusarMaisDeDuasCasasDecimaisEmVezDeArredondar() {
            assertThatThrownBy(() -> compra("10.555"))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("2 casas decimais");
        }

        @Test
        void deveExigirValorTipoEUsuario() {
            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, TipoTransacao.DEPOSITO, null, null, null, null))
                    .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("'valor'");
            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, null, BigDecimal.TEN, null, null, null))
                    .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("'tipo'");
            assertThatThrownBy(() -> Transacao.solicitar(null, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, null))
                    .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("'usuarioId'");
        }

        @ParameterizedTest
        @EnumSource(value = TipoTransacao.class, names = {"RETIRADA", "COMPRA"})
        void saidaSemCategoriaDeveSerRecusada(TipoTransacao tipo) {
            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, tipo, BigDecimal.TEN, null, null, null))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("'categoria' é obrigatório");
        }

        @Test
        void depositoNaoAceitaCategoria() {
            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, TipoTransacao.DEPOSITO, BigDecimal.TEN,
                    Categoria.LAZER, null, null))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("não se aplica");
        }

        @Test
        void transferenciaExigeDestinoDiferenteDoProprioUsuario() {
            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, TipoTransacao.TRANSFERENCIA, BigDecimal.TEN,
                    Categoria.OUTROS, null, null))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("'usuarioDestinoId'");

            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, TipoTransacao.TRANSFERENCIA, BigDecimal.TEN,
                    Categoria.OUTROS, USUARIO, null))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("próprio usuário");
        }

        @Test
        void destinoSoSeAplicaATransferencia() {
            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, TipoTransacao.COMPRA, BigDecimal.TEN,
                    Categoria.LAZER, OUTRO_USUARIO, null))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("só se aplica a TRANSFERENCIA");
        }

        @Test
        void deveRecusarValorComMaisDe13DigitosInteiros() {
            assertThatThrownBy(() -> compra("12345678901234.00"))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessage(MensagensTransacao.VALOR_FORMATO);
            assertThat(compra("1234567890123.99").getValor()).isEqualByComparingTo("1234567890123.99");
        }

        @Test
        void depositoNaoPrecisaDeSaldoESaidaPrecisa() {
            assertThat(deposito("1.00").precisaDeSaldo()).isFalse();
            assertThat(compra("1.00").precisaDeSaldo()).isTrue();
        }

        @Test
        void descricaoEmBrancoViraNuloEDescricaoLongaERecusada() {
            Transacao t = Transacao.solicitar(USUARIO, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, "   ");
            assertThat(t.getDescricao()).isNull();

            assertThatThrownBy(() -> Transacao.solicitar(USUARIO, TipoTransacao.DEPOSITO, BigDecimal.TEN,
                    null, null, "x".repeat(141)))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("140");
        }
    }

    // ---------- aprovação e rejeição ----------

    @Nested
    class Avaliar {

        @Test
        void depositoESempreAprovadoMesmoComContaZerada() {
            Transacao t = deposito("99999.99");
            t.avaliar(new SituacaoConta(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), new BigDecimal("999"));

            assertThat(t.getStatus()).isEqualTo(StatusTransacao.APROVADA);
            assertThat(t.isDespesa()).isFalse();
        }

        @Test
        void saidaDentroDoSaldoEDosLimitesEAprovada() {
            Transacao t = compra("500.00"); // exatamente no limite por transação
            t.avaliar(CONTA, new BigDecimal("300.00")); // 300 + 500 = 800, exatamente no limite diário

            assertThat(t.getStatus()).isEqualTo(StatusTransacao.APROVADA);
            assertThat(t.getMotivoRejeicao()).isNull();
            assertThat(t.isDespesa()).isTrue();
        }

        @Test
        void retiradaAcimaDoSaldoERejeitadaComMotivo() {
            Transacao t = Transacao.solicitar(USUARIO, TipoTransacao.RETIRADA, new BigDecimal("150.00"),
                    Categoria.OUTROS, null, null);
            t.avaliar(new SituacaoConta(new BigDecimal("100.00"), new BigDecimal("500"), new BigDecimal("800")),
                    BigDecimal.ZERO);

            assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
            assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SALDO_INSUFICIENTE);
            assertThat(t.isDespesa()).isFalse();
        }

        @Test
        void compraAcimaDoLimitePorTransacaoERejeitada() {
            Transacao t = compra("500.01");
            t.avaliar(CONTA, BigDecimal.ZERO);

            assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.LIMITE_POR_TRANSACAO_EXCEDIDO);
        }

        @Test
        void saidaQueEstouraOLimiteDiarioERejeitada() {
            Transacao t = compra("100.00");
            t.avaliar(CONTA, new BigDecimal("700.01"));

            assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.LIMITE_DIARIO_EXCEDIDO);
        }

        @Test
        void depositoPodeSerAvaliadoSemConta() {
            Transacao t = deposito("10.00");
            t.avaliar(null, null);

            assertThat(t.getStatus()).isEqualTo(StatusTransacao.APROVADA);
        }

        @Test
        void saidaSemContaNaoPodeSerAvaliada() {
            assertThatThrownBy(() -> compra("10.00").avaliar(null, BigDecimal.ZERO))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void totalDoDiaNuloContaComoZero() {
            Transacao t = compra("100.00");
            t.avaliar(CONTA, null);

            assertThat(t.getStatus()).isEqualTo(StatusTransacao.APROVADA);
        }

        @Test
        void saldoInsuficienteTemPrioridadeSobreOsLimites() {
            Transacao t = compra("2000.00"); // estoura saldo, limite por transação e diário
            t.avaliar(CONTA, BigDecimal.ZERO);

            assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SALDO_INSUFICIENTE);
        }

        @Test
        void transferenciaDescontaComoSaida() {
            Transacao t = Transacao.solicitar(USUARIO, TipoTransacao.TRANSFERENCIA, new BigDecimal("1000.01"),
                    Categoria.OUTROS, OUTRO_USUARIO, null);
            t.avaliar(CONTA, BigDecimal.ZERO);

            assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SALDO_INSUFICIENTE);
        }

        @Test
        void rejeitarDiretoQuandoServicoDeSaldoEstaFora() {
            Transacao t = compra("10.00");
            t.rejeitar(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);

            assertThat(t.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
            assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SERVICO_SALDO_INDISPONIVEL);
        }

        @Test
        void rejeitarExigeMotivo() {
            assertThatThrownBy(() -> compra("10.00").rejeitar(null))
                    .isInstanceOf(DadosInvalidosException.class);
        }

        @Test
        void transacaoDecididaNaoPodeMudar() {
            Transacao aprovada = compra("10.00");
            aprovada.avaliar(CONTA, BigDecimal.ZERO);

            assertThatThrownBy(() -> aprovada.rejeitar(MotivoRejeicao.SALDO_INSUFICIENTE))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
            assertThatThrownBy(() -> aprovada.avaliar(CONTA, BigDecimal.ZERO))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
            assertThat(aprovada.getStatus()).isEqualTo(StatusTransacao.APROVADA);
        }

        @Test
        void reconstituidaMantemOsDadosGravados() {
            Transacao t = Transacao.reconstituir(7L, USUARIO, TipoTransacao.COMPRA, new BigDecimal("20.00"),
                    Categoria.LAZER, null, "Cinema", java.time.Instant.EPOCH,
                    StatusTransacao.REJEITADA, MotivoRejeicao.LIMITE_DIARIO_EXCEDIDO);

            assertThat(t.getId()).isEqualTo(7L);
            assertThat(t.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.LIMITE_DIARIO_EXCEDIDO);
            assertThatThrownBy(() -> t.avaliar(CONTA, BigDecimal.ZERO))
                    .isInstanceOf(TransicaoDeStatusInvalidaException.class);
        }
    }

    @Test
    void situacaoContaRecusaValoresNegativosOuNulos() {
        assertThatThrownBy(() -> new SituacaoConta(new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("'saldo'");
        assertThatThrownBy(() -> new SituacaoConta(BigDecimal.ONE, null, BigDecimal.ONE))
                .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("'limitePorTransacao'");
    }
}
