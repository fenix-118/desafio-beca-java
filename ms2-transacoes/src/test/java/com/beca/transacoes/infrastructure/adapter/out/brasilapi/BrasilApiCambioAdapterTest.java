package com.beca.transacoes.infrastructure.adapter.out.brasilapi;

import com.beca.transacoes.domain.model.Cotacao;
import com.beca.transacoes.domain.model.MoedaEstrangeira;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Respostas copiadas da BrasilAPI real em 10/11/2026 (terça). */
class BrasilApiCambioAdapterTest {

    static final String API = "http://brasilapi-teste/api";

    /** Terça, 10/11/2026, 16:00 em São Paulo. */
    static final Clock TERCA = Clock.fixed(Instant.parse("2026-11-10T19:00:00Z"), ZoneOffset.UTC);
    static final LocalDate HOJE = LocalDate.of(2026, 11, 10);
    static final LocalDate ONTEM = LocalDate.of(2026, 11, 9);
    static final LocalDate DOMINGO = LocalDate.of(2026, 11, 8);

    static final String USD_SEGUNDA = """
            {"cotacoes":[
              {"paridade_compra":1,"paridade_venda":1,"cotacao_compra":5.5059,"cotacao_venda":5.5065,"data_hora_cotacao":"2026-11-09 10:09:14.44952","tipo_boletim":"ABERTURA"},
              {"paridade_compra":1,"paridade_venda":1,"cotacao_compra":5.5235,"cotacao_venda":5.5242,"data_hora_cotacao":"2026-11-09 13:11:18.845204","tipo_boletim":"INTERMEDIÁRIO"},
              {"paridade_compra":1,"paridade_venda":1,"cotacao_compra":5.514,"cotacao_venda":5.5146,"data_hora_cotacao":"2026-11-09 13:11:19.041465","tipo_boletim":"FECHAMENTO PTAX"}],
             "moeda":"USD","data":"2026-11-09"}
            """;

    static final String EUR_DOMINGO_DEVOLVE_SEXTA = """
            {"cotacoes":[
              {"paridade_compra":1.1167,"paridade_venda":1.1169,"cotacao_compra":6.1465,"cotacao_venda":6.1483,"data_hora_cotacao":"2026-11-06 10:02:16.317608","tipo_boletim":"ABERTURA"},
              {"paridade_compra":1.1144,"paridade_venda":1.1146,"cotacao_compra":6.13,"cotacao_venda":6.1317,"data_hora_cotacao":"2026-11-06 13:06:13.966362","tipo_boletim":"FECHAMENTO PTAX"}],
             "moeda":"EUR","data":"2026-11-06"}
            """;

    MockRestServiceServer api;
    BrasilApiCambioAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        api = MockRestServiceServer.bindTo(builder).build();
        adapter = new BrasilApiCambioAdapter(builder.build(), TERCA);
    }

    @Test
    void transacaoDeHojeConsultaODiaAnteriorEUsaOFechamentoPtaxDeVenda() {
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(USD_SEGUNDA, MediaType.APPLICATION_JSON));

        Cotacao cotacao = adapter.cotacao(MoedaEstrangeira.USD, HOJE).orElseThrow();

        assertThat(cotacao.taxa()).isEqualByComparingTo("5.5146");
        assertThat(cotacao.data()).isEqualTo(ONTEM);
        api.verify();
    }

    @Test
    void domingoGuardaADataDaRespostaQueESexta() {
        api.expect(requestTo(API + "/cambio/v1/cotacao/EUR/2026-11-08"))
                .andRespond(withSuccess(EUR_DOMINGO_DEVOLVE_SEXTA, MediaType.APPLICATION_JSON));

        Cotacao cotacao = adapter.cotacao(MoedaEstrangeira.EUR, DOMINGO).orElseThrow();

        assertThat(cotacao.data()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(cotacao.taxa()).isEqualByComparingTo("6.1317");
    }

    @Test
    void dataFuturaTambemConsultaOOntem() {
        assertThat(adapter.dataParaConsultar(LocalDate.of(2026, 12, 25))).isEqualTo(ONTEM);
        assertThat(adapter.dataParaConsultar(HOJE)).isEqualTo(ONTEM);
        assertThat(adapter.dataParaConsultar(DOMINGO)).isEqualTo(DOMINGO);
    }

    @Test
    void semFechamentoPtaxUsaOUltimoBoletim() {
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withSuccess("""
                        {"cotacoes":[{"cotacao_venda":5.50,"tipo_boletim":"ABERTURA"},
                                     {"cotacao_venda":5.52,"tipo_boletim":"INTERMEDIÁRIO"}],
                         "moeda":"USD","data":"2026-11-09"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.cotacao(MoedaEstrangeira.USD, HOJE).orElseThrow().taxa()).isEqualByComparingTo("5.52");
    }

    @Test
    void segundaConsultaDoMesmoDiaVemDoCacheSemChamarAApi() {
        api.expect(ExpectedCount.once(), requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withSuccess(USD_SEGUNDA, MediaType.APPLICATION_JSON));

        adapter.cotacao(MoedaEstrangeira.USD, HOJE);
        Optional<Cotacao> deNovo = adapter.cotacao(MoedaEstrangeira.USD, HOJE);

        assertThat(deNovo).isPresent();
        api.verify();
    }

    @Test
    void erro400DoDiaAtualViraConversaoIndisponivel() {
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"message":"Não é possível consultar a cotação do dia de hoje devido a política de cache",
                         "type":"today_date_error","name":"NO_TODAY_DATE"}
                        """));

        assertThat(adapter.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();
    }

    /** Um adapter novo, com a API simulada e o relógio informado. */
    private BrasilApiCambioAdapter adapterCom(Clock relogio) {
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        api = MockRestServiceServer.bindTo(builder).build();
        return new BrasilApiCambioAdapter(builder.build(), relogio, Duration.ofMinutes(5));
    }

    @Test
    void erro5xxTempoLimiteOuSemRedeViramConversaoIndisponivel() {
        BrasilApiCambioAdapter com5xx = adapterCom(TERCA);
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09")).andRespond(withServerError());
        assertThat(com5xx.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();

        BrasilApiCambioAdapter comTempoLimite = adapterCom(TERCA);
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThat(comTempoLimite.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();

        BrasilApiCambioAdapter semRede = adapterCom(TERCA);
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withException(new IOException("Connection refused")));
        assertThat(semRede.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();
    }

    @Test
    void depoisDeUmaFalhaAsConsultasFicamEmPausaSemChamarAApi() {
        RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-10T19:00:00Z"));
        BrasilApiCambioAdapter pausavel = adapterCom(relogio);
        api.expect(ExpectedCount.once(), requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(pausavel.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();
        relogio.avancar(Duration.ofMinutes(4));
        // Nem o USD de novo nem o EUR chamam a API: a espera do tempo limite não se repete.
        assertThat(pausavel.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();
        assertThat(pausavel.cotacao(MoedaEstrangeira.EUR, HOJE)).isEmpty();
        api.verify();
    }

    @Test
    void passadaAPausaAApiEConsultadaDeNovo() {
        RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-10T19:00:00Z"));
        BrasilApiCambioAdapter pausavel = adapterCom(relogio);
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09")).andRespond(withServerError());
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withSuccess(USD_SEGUNDA, MediaType.APPLICATION_JSON));

        assertThat(pausavel.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();
        relogio.avancar(Duration.ofMinutes(5));
        assertThat(pausavel.cotacao(MoedaEstrangeira.USD, HOJE)).isPresent();
        api.verify();
    }

    @Test
    void cotacaoJaEmCacheContinuaValendoDuranteAPausa() {
        RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-10T19:00:00Z"));
        BrasilApiCambioAdapter pausavel = adapterCom(relogio);
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withSuccess(USD_SEGUNDA, MediaType.APPLICATION_JSON));
        api.expect(requestTo(API + "/cambio/v1/cotacao/EUR/2026-11-09")).andRespond(withServerError());

        pausavel.cotacao(MoedaEstrangeira.USD, HOJE);
        assertThat(pausavel.cotacao(MoedaEstrangeira.EUR, HOJE)).isEmpty();
        assertThat(pausavel.cotacao(MoedaEstrangeira.USD, HOJE)).isPresent();
        api.verify();
    }

    @Test
    void recusa4xxNaoPausaAsConsultas() {
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        api.expect(requestTo(API + "/cambio/v1/cotacao/EUR/2026-11-09"))
                .andRespond(withSuccess(EUR_DOMINGO_DEVOLVE_SEXTA, MediaType.APPLICATION_JSON));

        assertThat(adapter.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();
        assertThat(adapter.cotacao(MoedaEstrangeira.EUR, HOJE)).isPresent();
        api.verify();
    }

    /** Relógio que o teste avança, para simular a passagem do tempo da pausa. */
    static final class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return Clock.fixed(agora, zona);
        }
    }

    @Test
    void respostaNumFormatoInesperadoViraConversaoIndisponivel() {
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withSuccess("<html>manutenção</html>", MediaType.TEXT_HTML));
        assertThat(adapter.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();

        api.reset();
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withSuccess("{\"cotacoes\":[],\"moeda\":\"USD\",\"data\":\"2026-11-09\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(adapter.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();

        api.reset();
        api.expect(requestTo(API + "/cambio/v1/cotacao/USD/2026-11-09"))
                .andRespond(withSuccess("{\"cotacoes\":[{\"cotacao_venda\":0,\"tipo_boletim\":\"FECHAMENTO PTAX\"}],"
                        + "\"moeda\":\"USD\",\"data\":\"2026-11-09\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.cotacao(MoedaEstrangeira.USD, HOJE)).isEmpty();
    }
}
