package com.beca.transacoes.infrastructure.adapter.out.persistence;

import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.Cotacao;
import com.beca.transacoes.domain.model.MoedaEstrangeira;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa as consultas de verdade (tarefa 8.9): o esquema vem da migração V1 do Flyway, num H2 em memória,
 * e cada teste é desfeito ao terminar. A validação do mapeamento contra o PostgreSQL fica no ddl-auto: validate
 * da aplicação; aqui o Hibernate não mexe no esquema.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@Import(TransacaoPersistenceAdapter.class)
class TransacaoConsultasJpaTest {

    private static final Long ANA = 3L;
    private static final Long BRUNO = 4L;

    /** Dia 10/11/2026 em São Paulo = [03:00 UTC de 10/11, 03:00 UTC de 11/11). */
    private static final Instant INICIO_DIA = Instant.parse("2026-11-10T03:00:00Z");
    private static final Instant FIM_DIA = Instant.parse("2026-11-11T03:00:00Z");

    @Autowired
    private TransacaoPersistenceAdapter adapter;

    private Transacao gravar(Long usuario, TipoTransacao tipo, String valor, StatusTransacao status, String quando) {
        Categoria categoria = tipo.isSaida() ? Categoria.OUTROS : null;
        Long destino = tipo == TipoTransacao.TRANSFERENCIA ? BRUNO : null;
        MotivoRejeicao motivo = status == StatusTransacao.REJEITADA ? MotivoRejeicao.SALDO_INSUFICIENTE : null;
        return adapter.salvar(Transacao.reconstituir(null, usuario, tipo, new BigDecimal(valor), categoria, destino,
                null, Instant.parse(quando), status, motivo));
    }

    // ---------- soma das saídas aprovadas (limite diário) ----------

    @Test
    void somaSoAsSaidasAprovadasDoUsuarioNoDia() {
        gravar(ANA, TipoTransacao.COMPRA, "100.00", StatusTransacao.APROVADA, "2026-11-10T12:00:00Z");
        gravar(ANA, TipoTransacao.RETIRADA, "50.00", StatusTransacao.APROVADA, "2026-11-10T13:00:00Z");
        gravar(ANA, TipoTransacao.TRANSFERENCIA, "25.00", StatusTransacao.APROVADA, "2026-11-10T14:00:00Z");
        gravar(ANA, TipoTransacao.COMPRA, "999.00", StatusTransacao.REJEITADA, "2026-11-10T15:00:00Z"); // rejeitada
        gravar(ANA, TipoTransacao.DEPOSITO, "500.00", StatusTransacao.APROVADA, "2026-11-10T16:00:00Z"); // entrada
        gravar(BRUNO, TipoTransacao.COMPRA, "70.00", StatusTransacao.APROVADA, "2026-11-10T12:00:00Z"); // outro usuário

        assertThat(adapter.somarSaidasAprovadas(ANA, INICIO_DIA, FIM_DIA)).isEqualByComparingTo("175.00");
    }

    @Test
    void oInicioDoDiaEntraEOFimNao() {
        gravar(ANA, TipoTransacao.COMPRA, "10.00", StatusTransacao.APROVADA, "2026-11-10T03:00:00Z"); // 00:00 em SP
        gravar(ANA, TipoTransacao.COMPRA, "20.00", StatusTransacao.APROVADA, "2026-11-11T02:59:59Z"); // 23:59:59 em SP
        gravar(ANA, TipoTransacao.COMPRA, "40.00", StatusTransacao.APROVADA, "2026-11-11T03:00:00Z"); // já é dia 11

        assertThat(adapter.somarSaidasAprovadas(ANA, INICIO_DIA, FIM_DIA)).isEqualByComparingTo("30.00");
    }

    @Test
    void semSaidasNoDiaASomaEZeroENaoNulo() {
        gravar(ANA, TipoTransacao.DEPOSITO, "500.00", StatusTransacao.APROVADA, "2026-11-10T12:00:00Z");

        assertThat(adapter.somarSaidasAprovadas(ANA, INICIO_DIA, FIM_DIA)).isEqualByComparingTo("0");
    }

    // ---------- listagem por período e tipo ----------

    @Test
    void listaDoUsuarioDaMaisRecenteParaAMaisAntiga() {
        Transacao primeira = gravar(ANA, TipoTransacao.DEPOSITO, "500.00", StatusTransacao.APROVADA, "2026-11-01T12:00:00Z");
        Transacao segunda = gravar(ANA, TipoTransacao.COMPRA, "30.00", StatusTransacao.APROVADA, "2026-11-05T12:00:00Z");
        Transacao terceira = gravar(ANA, TipoTransacao.COMPRA, "40.00", StatusTransacao.REJEITADA, "2026-11-09T12:00:00Z");
        gravar(BRUNO, TipoTransacao.COMPRA, "70.00", StatusTransacao.APROVADA, "2026-11-05T12:00:00Z");

        List<Transacao> lista = adapter.listar(ANA, null, null, EnumSet.allOf(TipoTransacao.class));

        assertThat(lista).extracting(Transacao::getId)
                .containsExactly(terceira.getId(), segunda.getId(), primeira.getId());
    }

    @Test
    void filtraPeloPeriodoEPeloTipo() {
        gravar(ANA, TipoTransacao.COMPRA, "10.00", StatusTransacao.APROVADA, "2026-10-31T12:00:00Z"); // fora do período
        Transacao dentro = gravar(ANA, TipoTransacao.COMPRA, "20.00", StatusTransacao.APROVADA, "2026-11-05T12:00:00Z");
        gravar(ANA, TipoTransacao.DEPOSITO, "30.00", StatusTransacao.APROVADA, "2026-11-05T13:00:00Z"); // outro tipo
        gravar(ANA, TipoTransacao.COMPRA, "40.00", StatusTransacao.APROVADA, "2026-12-01T12:00:00Z"); // fora do período

        List<Transacao> lista = adapter.listar(ANA, Instant.parse("2026-11-01T03:00:00Z"),
                Instant.parse("2026-12-01T03:00:00Z"), EnumSet.of(TipoTransacao.COMPRA));

        assertThat(lista).extracting(Transacao::getId).containsExactly(dentro.getId());
    }

    @Test
    void usuarioSemTransacoesRecebeListaVazia() {
        gravar(BRUNO, TipoTransacao.COMPRA, "70.00", StatusTransacao.APROVADA, "2026-11-05T12:00:00Z");

        assertThat(adapter.listar(ANA, null, null, EnumSet.allOf(TipoTransacao.class))).isEmpty();
    }

    // ---------- gravação e leitura ----------

    @Test
    void gravaELeTodosOsCamposIncluindoOCambio() {
        Transacao compra = Transacao.reconstituir(null, ANA, TipoTransacao.COMPRA, new BigDecimal("120.00"),
                Categoria.ALIMENTACAO, null, "Mercado", Instant.parse("2026-11-10T14:39:13Z"),
                StatusTransacao.APROVADA, null, List.of(
                        new Cotacao(MoedaEstrangeira.USD, new BigDecimal("5.514600"), LocalDate.of(2026, 11, 9)),
                        new Cotacao(MoedaEstrangeira.EUR, new BigDecimal("6.131700"), LocalDate.of(2026, 11, 6))));

        Transacao lida = adapter.buscarPorId(adapter.salvar(compra).getId()).orElseThrow();

        assertThat(lida.getUsuarioId()).isEqualTo(ANA);
        assertThat(lida.getTipo()).isEqualTo(TipoTransacao.COMPRA);
        assertThat(lida.getValor()).isEqualByComparingTo("120.00");
        assertThat(lida.getCategoria()).isEqualTo(Categoria.ALIMENTACAO);
        assertThat(lida.getDescricao()).isEqualTo("Mercado");
        assertThat(lida.getStatus()).isEqualTo(StatusTransacao.APROVADA);
        assertThat(lida.getCriadaEm()).isEqualTo(Instant.parse("2026-11-10T14:39:13Z"));
        assertThat(lida.cotacao(MoedaEstrangeira.USD)).hasValueSatisfying(c -> {
            assertThat(c.taxa()).isEqualByComparingTo("5.5146");
            assertThat(c.data()).isEqualTo(LocalDate.of(2026, 11, 9));
        });
        assertThat(lida.cotacao(MoedaEstrangeira.EUR)).isPresent();
    }

    @Test
    void transacaoRejeitadaGuardaOMotivo() {
        Transacao rejeitada = gravar(ANA, TipoTransacao.COMPRA, "999.00", StatusTransacao.REJEITADA,
                "2026-11-10T12:00:00Z");

        Transacao lida = adapter.buscarPorId(rejeitada.getId()).orElseThrow();

        assertThat(lida.getStatus()).isEqualTo(StatusTransacao.REJEITADA);
        assertThat(lida.getMotivoRejeicao()).isEqualTo(MotivoRejeicao.SALDO_INSUFICIENTE);
    }
}
