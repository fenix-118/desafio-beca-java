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
