package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.GerarRelatorioUseCase.GerarRelatorioQuery;
import com.beca.transacoes.application.port.in.GerarRelatorioUseCase.RelatorioDoPeriodo;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
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
class RelatorioServiceTest {

    /** 10/11 às 13:00 UTC = 10:00 em São Paulo. */
    private static final Instant AGORA = Instant.parse("2026-11-10T13:00:00Z");
    private static final EnumSet<TipoTransacao> TODOS = EnumSet.allOf(TipoTransacao.class);
    private static final Instant INICIO_NOV_SP = Instant.parse("2026-11-01T03:00:00Z");
    private static final Instant INICIO_DEZ_SP = Instant.parse("2026-12-01T03:00:00Z");

    @Mock
    private TransacaoRepositoryPort repositorio;

    private RelatorioService service;

    @BeforeEach
    void setUp() {
        service = new RelatorioService(repositorio, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Transacao transacao(long id, TipoTransacao tipo, StatusTransacao status) {
        return Transacao.reconstituir(id, 3L, tipo, new BigDecimal("10.00"), null, null, null,
                AGORA.minusSeconds(id), status, null);
    }

    @Test
    void trazTodosOsTiposEStatusDoPeriodoDaMaisAntigaParaAMaisRecente() {
        // O repositório devolve da mais recente para a mais antiga.
        when(repositorio.listar(3L, INICIO_NOV_SP, INICIO_DEZ_SP, TODOS)).thenReturn(List.of(
                transacao(2L, TipoTransacao.DEPOSITO, StatusTransacao.APROVADA),
                transacao(1L, TipoTransacao.RETIRADA, StatusTransacao.REJEITADA)));

        RelatorioDoPeriodo r = service.gerar(
                new GerarRelatorioQuery(3L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));

        assertThat(r.usuarioId()).isEqualTo(3L);
        assertThat(r.inicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.fim()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(r.transacoes()).extracting(Transacao::getId).containsExactly(1L, 2L);
    }

    @Test
    void semPeriodoUsaOMesAtualEmSaoPaulo() {
        when(repositorio.listar(3L, INICIO_NOV_SP, INICIO_DEZ_SP, TODOS)).thenReturn(List.of());

        RelatorioDoPeriodo r = service.gerar(new GerarRelatorioQuery(3L, null, null));

        assertThat(r.inicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.fim()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(r.transacoes()).isEmpty();
    }

    @Test
    void periodoComSoUmaDataOuInvertidoOuLongoDemaisResponde400SemConsultarOBanco() {
        LocalDate dia = LocalDate.of(2026, 11, 1);
        assertThatThrownBy(() -> service.gerar(new GerarRelatorioQuery(3L, dia, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(PeriodoSaoPaulo.PERIODO_INCOMPLETO);
        assertThatThrownBy(() -> service.gerar(new GerarRelatorioQuery(3L, dia, dia.minusDays(1))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(PeriodoSaoPaulo.PERIODO_INVALIDO);
        assertThatThrownBy(() -> service.gerar(new GerarRelatorioQuery(3L, dia, dia.plusDays(366))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(PeriodoSaoPaulo.PERIODO_LONGO_DEMAIS);
        verifyNoInteractions(repositorio);
    }

    @Test
    void usuarioInvalidoResponde400() {
        assertThatThrownBy(() -> service.gerar(new GerarRelatorioQuery(null, null, null)))
                .isInstanceOf(DadosInvalidosException.class);
        verifyNoInteractions(repositorio);
    }
}
