package com.beca.transacoes.infrastructure.adapter.out.usuarios;

import com.beca.transacoes.application.port.out.FalhaIntegracaoUsuariosException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Simula o MS1 com o MockRestServiceServer: nenhuma chamada de rede de verdade. */
class UsuarioRestAdapterTest {

    private static final String MS1 = "http://ms1-teste";

    private MockRestServiceServer ms1;
    private UsuarioRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(MS1);
        ms1 = MockRestServiceServer.bindTo(builder).build();
        adapter = new UsuarioRestAdapter(builder.build());
    }

    @Test
    void usuarioExisteQuandoOMs1Responde200() {
        ms1.expect(requestTo(MS1 + "/usuarios/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        assertThat(adapter.existe(1L)).isTrue();
        ms1.verify();
    }

    @Test
    void usuarioNaoExisteQuandoOMs1Responde404() {
        ms1.expect(requestTo(MS1 + "/usuarios/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.existe(99L)).isFalse();
        ms1.verify();
    }

    @Test
    void erro5xxDoMs1ViraServicoIndisponivel() {
        ms1.expect(requestTo(MS1 + "/usuarios/1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.existe(1L))
                .isInstanceOf(ServicoUsuariosIndisponivelException.class);
    }

    @Test
    void ms1ForaDoArViraServicoIndisponivel() {
        ms1.expect(requestTo(MS1 + "/usuarios/1"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.existe(1L))
                .isInstanceOf(ServicoUsuariosIndisponivelException.class);
    }

    @Test
    void outro4xxDoMs1EErroDeIntegracaoComOStatusENaoIndisponivel() {
        ms1.expect(requestTo(MS1 + "/usuarios/1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter.existe(1L))
                .isInstanceOf(FalhaIntegracaoUsuariosException.class)
                .hasMessageContaining("403");
    }
}
