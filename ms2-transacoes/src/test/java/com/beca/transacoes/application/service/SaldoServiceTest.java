package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.ConsultarSaldoUseCase.SaldoDaConta;
import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.domain.exception.ContaNaoEncontradaException;
import com.beca.transacoes.domain.exception.UsuarioNaoEncontradoException;
import com.beca.transacoes.domain.model.SituacaoConta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaldoServiceTest {

    /** 10/11 às 13:00 UTC = 10:00 em São Paulo. */
    private static final Instant AGORA = Instant.parse("2026-11-10T13:00:00Z");
    private static final Instant INICIO_DO_DIA_SP = Instant.parse("2026-11-10T03:00:00Z");
    private static final Instant FIM_DO_DIA_SP = Instant.parse("2026-11-11T03:00:00Z");

    private static final SituacaoConta CONTA = new SituacaoConta(
            new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("800.00"));

    @Mock
    private UsuarioPort usuarios;

    @Mock
    private ContaPort conta;

    @Mock
    private TransacaoRepositoryPort repositorio;

    private SaldoService service;

    @BeforeEach
    void setUp() {
        service = new SaldoService(usuarios, conta, repositorio, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void devolveOValorDaMockEOQueAindaPodeSairHoje() {
        when(usuarios.existe(3L)).thenReturn(true);
        when(conta.consultar(3L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(3L, INICIO_DO_DIA_SP, FIM_DO_DIA_SP)).thenReturn(new BigDecimal("520.00"));

        SaldoDaConta saldo = service.consultar(3L);

        assertThat(saldo.saldo()).isEqualByComparingTo("1000.00");
        assertThat(saldo.limitePorTransacao()).isEqualByComparingTo("500.00");
        assertThat(saldo.gastoHoje()).isEqualByComparingTo("520.00");
        assertThat(saldo.limiteDiarioDisponivel()).isEqualByComparingTo("280.00");
        assertThat(saldo.consultadoEm()).isEqualTo(AGORA);
    }

    @Test
    void limiteDisponivelNuncaFicaNegativo() {
        when(usuarios.existe(3L)).thenReturn(true);
        when(conta.consultar(3L)).thenReturn(Optional.of(CONTA));
        when(repositorio.somarSaidasAprovadas(3L, INICIO_DO_DIA_SP, FIM_DO_DIA_SP)).thenReturn(new BigDecimal("900.00"));

        assertThat(service.consultar(3L).limiteDiarioDisponivel()).isEqualByComparingTo("0");
    }

    @Test
    void usuarioInexistenteNoMs1Responde404SemConsultarAMock() {
        when(usuarios.existe(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.consultar(99L)).isInstanceOf(UsuarioNaoEncontradoException.class);
        verifyNoInteractions(conta, repositorio);
    }

    @Test
    void usuarioSemContaResponde404() {
        when(usuarios.existe(9L)).thenReturn(true);
        when(conta.consultar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(9L))
                .isInstanceOf(ContaNaoEncontradaException.class)
                .hasMessageContaining("9");
        verifyNoInteractions(repositorio);
    }

    @Test
    void mockForaDoArChegaAoControllerComo503() {
        when(usuarios.existe(3L)).thenReturn(true);
        when(conta.consultar(3L)).thenThrow(new ServicoSaldoIndisponivelException("timeout", null));

        assertThatThrownBy(() -> service.consultar(3L)).isInstanceOf(ServicoSaldoIndisponivelException.class);
    }
}
