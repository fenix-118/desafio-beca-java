package com.beca.transacoes.application.service;

import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase.RegistrarTransacaoCommand;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransacaoServiceTest {

    @Mock
    private TransacaoRepositoryPort repositorio;

    @InjectMocks
    private TransacaoService service;

    @Test
    void depositoDeveSerAprovadoEGravado() {
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        Transacao t = service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, new BigDecimal("250"), null, null, "Salário"));

        ArgumentCaptor<Transacao> salva = ArgumentCaptor.forClass(Transacao.class);
        verify(repositorio).salvar(salva.capture());
        assertThat(salva.getValue().getStatus()).isEqualTo(StatusTransacao.APROVADA);
        assertThat(salva.getValue().getValor()).isEqualByComparingTo("250.00");
        assertThat(t).isSameAs(salva.getValue());
    }

    @Test
    void dadosInvalidosNaoChegamAoBanco() {
        assertThatThrownBy(() -> service.registrar(new RegistrarTransacaoCommand(
                1L, TipoTransacao.DEPOSITO, new BigDecimal("-5"), null, null, null)))
                .isInstanceOf(DadosInvalidosException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    void buscarInexistenteLancaNaoEncontrada() {
        when(repositorio.buscarPorId(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(42L))
                .isInstanceOf(TransacaoNaoEncontradaException.class)
                .hasMessageContaining("42");
    }
}
