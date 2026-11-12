package com.beca.transacoes.infrastructure.adapter.in.web.relatorio;

import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioCsvTest {

    /** 10/11 às 15:30 UTC = 12:30 em São Paulo. */
    private static final Instant QUANDO = Instant.parse("2026-11-10T15:30:00Z");

    private static String csv(Transacao... transacoes) {
        return new String(RelatorioCsv.gerar(List.of(transacoes)), StandardCharsets.UTF_8);
    }

    @Test
    void semTransacoesTemSoOCabecalho() {
        assertThat(csv()).isEqualTo(
                "Data;Id;Tipo;Categoria;Descrição;Usuário destino;Valor (BRL);Status;Motivo da rejeição\r\n");
    }

    @Test
    void umaLinhaPorTransacaoComHoraDeSaoPauloEMotivoDaRejeicao() {
        Transacao rejeitada = Transacao.reconstituir(5L, 3L, TipoTransacao.TRANSFERENCIA, new BigDecimal("1500.00"),
                null, 4L, "Aluguel", QUANDO, StatusTransacao.REJEITADA, MotivoRejeicao.SALDO_INSUFICIENTE);

        String[] linhas = csv(rejeitada).split("\r\n");

        assertThat(linhas).hasSize(2);
        assertThat(linhas[1]).isEqualTo(
                "2026-11-10T12:30;5;TRANSFERENCIA;;Aluguel;4;1500.00;REJEITADA;Saldo insuficiente para a transação.");
    }

    @Test
    void campoComSeparadorAspasOuQuebraDeLinhaVaiEntreAspas() {
        Transacao compra = Transacao.reconstituir(6L, 3L, TipoTransacao.COMPRA, new BigDecimal("9.90"),
                Categoria.ALIMENTACAO, null, "Pão; leite \"integral\"", QUANDO, StatusTransacao.APROVADA, null);

        assertThat(csv(compra)).contains(";\"Pão; leite \"\"integral\"\"\";");
        assertThat(RelatorioCsv.campo("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
        assertThat(RelatorioCsv.campo("simples")).isEqualTo("simples");
    }
}
