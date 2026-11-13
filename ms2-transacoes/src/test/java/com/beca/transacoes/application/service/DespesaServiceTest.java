package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase.ConsultarDespesasQuery;
import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase.ResumoDoPeriodo;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DespesaServiceTest {

    /** 10/11 às 13:00 UTC = 10:00 em São Paulo. */
    private static final Instant AGORA = Instant.parse("2026-11-10T13:00:00Z");
    private static final EnumSet<TipoTransacao> SAIDAS =
            EnumSet.of(TipoTransacao.RETIRADA, TipoTransacao.TRANSFERENCIA, TipoTransacao.COMPRA);

    @Mock
    private TransacaoRepositoryPort repositorio;

    private DespesaService service;

    @BeforeEach
    void setUp() {
        service = new DespesaService(repositorio, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void consultaAsSaidasDoPeriodoEmDiasDeSaoPauloComOFimIncluido() {
        // 01/11 00:00 em SP = 03:00 UTC; o fim (30/11) vai até o início de 01/12 em SP.
        Instant de = Instant.parse("2026-11-01T03:00:00Z");
        Instant ate = Instant.parse("2026-12-01T03:00:00Z");
        when(repositorio.listar(3L, de, ate, SAIDAS)).thenReturn(List.of(
                Transacao.reconstituir(1L, 3L, TipoTransacao.COMPRA, new BigDecimal("80.00"), Categoria.LAZER,
                        null, null, AGORA, StatusTransacao.APROVADA, null)));

        ResumoDoPeriodo r = service.consultar(
                new ConsultarDespesasQuery(3L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));

        assertThat(r.usuarioId()).isEqualTo(3L);
        assertThat(r.inicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.fim()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(r.resumo().totalGeral()).isEqualByComparingTo("80.00");
    }

    @Test
    void semPeriodoUsaOMesAtualEmSaoPaulo() {
        // AGORA = 10/11/2026 em São Paulo: o mês atual vai de 01/11 a 30/11.
        when(repositorio.listar(3L, Instant.parse("2026-11-01T03:00:00Z"), Instant.parse("2026-12-01T03:00:00Z"), SAIDAS))
                .thenReturn(List.of());

        ResumoDoPeriodo r = service.consultar(new ConsultarDespesasQuery(3L, null, null));

        assertThat(r.inicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.fim()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(r.resumo().totalGeral()).isEqualByComparingTo("0");
    }

    @Test
    void oMesAtualEODeSaoPauloNaViradaDoMes() {
        // 01/12 às 01:00 UTC ainda é 30/11 às 22:00 em São Paulo: o mês atual é novembro.
        DespesaService naVirada = new DespesaService(repositorio,
                Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC));
        when(repositorio.listar(3L, Instant.parse("2026-11-01T03:00:00Z"), Instant.parse("2026-12-01T03:00:00Z"), SAIDAS))
                .thenReturn(List.of());

        ResumoDoPeriodo r = naVirada.consultar(new ConsultarDespesasQuery(3L, null, null));

        assertThat(r.inicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.fim()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void recusaPeriodoComSoUmaDasDatas() {
        assertThatThrownBy(() -> service.consultar(new ConsultarDespesasQuery(3L, LocalDate.of(2026, 11, 1), null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(DespesaService.PERIODO_INCOMPLETO);
        assertThatThrownBy(() -> service.consultar(new ConsultarDespesasQuery(3L, null, LocalDate.of(2026, 11, 30))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(DespesaService.PERIODO_INCOMPLETO);
        verifyNoInteractions(repositorio);
    }

    @Test
    void aceitaUmAnoInteiro() {
        when(repositorio.listar(3L, Instant.parse("2025-01-01T03:00:00Z"), Instant.parse("2026-01-01T03:00:00Z"), SAIDAS))
                .thenReturn(List.of());

        ResumoDoPeriodo r = service.consultar(
                new ConsultarDespesasQuery(3L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

        assertThat(r.fim()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void recusaPeriodoMaiorQueUmAno() {
        assertThatThrownBy(() -> service.consultar(
                new ConsultarDespesasQuery(3L, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 11, 30))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(DespesaService.PERIODO_LONGO_DEMAIS);
        verifyNoInteractions(repositorio);
    }

    @Test
    void recusaUsuarioInvalido() {
        assertThatThrownBy(() -> service.consultar(
                new ConsultarDespesasQuery(0L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))))
                .isInstanceOf(DadosInvalidosException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    void recusaInicioDepoisDoFim() {
        assertThatThrownBy(() -> service.consultar(
                new ConsultarDespesasQuery(3L, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(DespesaService.PERIODO_INVALIDO);
        verifyNoInteractions(repositorio);
    }
}
