package com.beca.transacoes.infrastructure.adapter.out.persistence;

import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.SituacaoConta;
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
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransacaoPersistenceAdapterTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T15:00:00Z");

    @Mock
    private TransacaoJpaRepository jpa;

    @InjectMocks
    private TransacaoPersistenceAdapter adapter;

    @Test
    void deveGravarTransacaoRejeitadaComMotivoEDevolverComId() {
        Transacao retirada = Transacao.solicitar(1L, TipoTransacao.RETIRADA, new BigDecimal("150.00"),
                Categoria.OUTROS, null, "Saque");
        retirada.avaliar(new SituacaoConta(new BigDecimal("100"), new BigDecimal("500"), new BigDecimal("800")),
                BigDecimal.ZERO);
        when(jpa.saveAndFlush(any())).thenAnswer(inv -> {
            TransacaoEntity e = inv.getArgument(0);
            return new TransacaoEntity(10L, e.getUsuarioId(), e.getTipo(), e.getValor(), e.getCategoria(),
                    e.getUsuarioDestinoId(), e.getDescricao(), e.getStatus(), e.getMotivoRejeicao(), e.getCriadaEm());
        });

        Transacao salva = adapter.salvar(retirada);

        ArgumentCaptor<TransacaoEntity> gravada = ArgumentCaptor.forClass(TransacaoEntity.class);
        verify(jpa).saveAndFlush(gravada.capture());
        assertThat(gravada.getValue().getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(gravada.getValue().getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SALDO_INSUFICIENTE);
        assertThat(salva.getId()).isEqualTo(10L);
        assertThat(salva.getValor()).isEqualByComparingTo("150.00");
        assertThat(salva.getDescricao()).isEqualTo("Saque");
    }

    @Test
    void naoDeveGravarTransacaoPendente() {
        Transacao pendente = Transacao.solicitar(1L, TipoTransacao.DEPOSITO, BigDecimal.TEN, null, null, null);

        assertThatThrownBy(() -> adapter.salvar(pendente)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jpa);
    }

    @Test
    void deveBuscarPorIdEConverterParaODominio() {
        when(jpa.findById(5L)).thenReturn(Optional.of(new TransacaoEntity(5L, 2L, TipoTransacao.TRANSFERENCIA,
                new BigDecimal("30.00"), Categoria.OUTROS, 3L, null, StatusTransacao.APROVADA, null, AGORA)));

        Transacao t = adapter.buscarPorId(5L).orElseThrow();

        assertThat(t.getUsuarioDestinoId()).isEqualTo(3L);
        assertThat(t.getStatus()).isEqualTo(StatusTransacao.APROVADA);
        assertThat(t.getCriadaEm()).isEqualTo(AGORA);
    }

    @Test
    void buscarInexistenteDevolveVazio() {
        when(jpa.findById(99L)).thenReturn(Optional.empty());

        assertThat(adapter.buscarPorId(99L)).isEmpty();
    }
}
