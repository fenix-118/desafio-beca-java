package com.beca.transacoes.infrastructure.adapter.out.mockapi;

import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.domain.model.SituacaoConta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Simula a API Mock com o MockRestServiceServer, com respostas iguais às do mockapi.io. */
class MockApiContaAdapterTest {

    static final String MOCK = "http://mock-teste/api/v1";

    /** O que a Mock devolve para ?usuarioId=3: o filtro dela é "contém", então o 13 vem junto. */
    static final String CONTAS_DO_3_E_DO_13 = """
            [{"id":"2","usuarioId":3,"saldo":1000,"limitePorTransacao":500,"limiteDiario":800},
             {"id":"7","usuarioId":13,"saldo":2000,"limitePorTransacao":500,"limiteDiario":1000}]
            """;

    MockRestServiceServer mock;
    MockApiContaAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(MOCK);
        mock = MockRestServiceServer.bindTo(builder).build();
        adapter = new MockApiContaAdapter(builder.build());
    }

    @Test
    void devolveSoAContaDoUsuarioExato() {
        mock.expect(requestTo(MOCK + "/contas?usuarioId=3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(CONTAS_DO_3_E_DO_13, MediaType.APPLICATION_JSON));

        Optional<SituacaoConta> conta = adapter.consultar(3L);

        assertThat(conta).isPresent();
        assertThat(conta.get().saldo()).isEqualByComparingTo("1000");
        assertThat(conta.get().limitePorTransacao()).isEqualByComparingTo("500");
        assertThat(conta.get().limiteDiario()).isEqualByComparingTo("800");
        mock.verify();
    }

    @Test
    void mantemOsCentavosDoSaldo() {
        mock.expect(requestTo(MOCK + "/contas?usuarioId=2"))
                .andRespond(withSuccess("""
                        [{"id":"1","usuarioId":2,"saldo":5000.75,"limitePorTransacao":1000,"limiteDiario":2000}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.consultar(2L).orElseThrow().saldo()).isEqualByComparingTo("5000.75");
    }

    @Test
    void notFoundDaMockSignificaUsuarioSemConta() {
        mock.expect(requestTo(MOCK + "/contas?usuarioId=9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body("\"Not found\""));

        assertThat(adapter.consultar(9L)).isEmpty();
    }

    @Test
    void listaSemOUsuarioExatoSignificaSemConta() {
        mock.expect(requestTo(MOCK + "/contas?usuarioId=1"))
                .andRespond(withSuccess("""
                        [{"id":"7","usuarioId":13,"saldo":2000,"limitePorTransacao":500,"limiteDiario":1000}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.consultar(1L)).isEmpty();
    }

    @Test
    void erro5xxViraServicoIndisponivel() {
        mock.expect(requestTo(MOCK + "/contas?usuarioId=3")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.consultar(3L)).isInstanceOf(ServicoSaldoIndisponivelException.class);
    }

    @Test
    void mockForaDoArViraServicoIndisponivel() {
        mock.expect(requestTo(MOCK + "/contas?usuarioId=3"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.consultar(3L)).isInstanceOf(ServicoSaldoIndisponivelException.class);
    }

    @Test
    void saldoNegativoNaMockViraServicoIndisponivelEmVezDe400() {
        mock.expect(requestTo(MOCK + "/contas?usuarioId=3"))
                .andRespond(withSuccess("""
                        [{"id":"2","usuarioId":3,"saldo":-5,"limitePorTransacao":500,"limiteDiario":800}]
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.consultar(3L)).isInstanceOf(ServicoSaldoIndisponivelException.class);
    }
}
