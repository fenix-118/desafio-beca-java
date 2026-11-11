package com.beca.transacoes.infrastructure.adapter.out.usuarios;

import com.beca.transacoes.application.port.out.FalhaIntegracaoUsuariosException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Simula o POST /autenticacao do MS1 com o MockRestServiceServer. */
class AutenticacaoRestAdapterTest {

    private static final String MS1 = "http://ms1-teste";

    private MockRestServiceServer ms1;
    private AutenticacaoRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(MS1);
        ms1 = MockRestServiceServer.bindTo(builder).build();
        adapter = new AutenticacaoRestAdapter(builder.build());
    }

    @Test
    void credenciaisQueConferemDevolvemOIdDoUsuario() {
        ms1.expect(requestTo(MS1 + "/autenticacao"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"email":"ana@beca.com","senha":"Senha@123"}
                        """))
                .andRespond(withSuccess("""
                        {"id":3,"nome":"Ana","email":"ana@beca.com"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.autenticar("ana@beca.com", "Senha@123")).contains(3L);
        ms1.verify();
    }

    @Test
    void ms1Respondendo401SignificaCredenciaisInvalidas() {
        ms1.expect(requestTo(MS1 + "/autenticacao"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(adapter.autenticar("ana@beca.com", "errada123")).isEmpty();
    }

    @Test
    void erro5xxDoMs1ViraServicoIndisponivel() {
        ms1.expect(requestTo(MS1 + "/autenticacao"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.autenticar("ana@beca.com", "Senha@123"))
                .isInstanceOf(ServicoUsuariosIndisponivelException.class);
    }

    @Test
    void ms1ForaDoArViraServicoIndisponivel() {
        ms1.expect(requestTo(MS1 + "/autenticacao"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.autenticar("ana@beca.com", "Senha@123"))
                .isInstanceOf(ServicoUsuariosIndisponivelException.class);
    }

    @Test
    void outro4xxDoMs1EErroDeIntegracao() {
        ms1.expect(requestTo(MS1 + "/autenticacao"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter.autenticar("ana@beca.com", "Senha@123"))
                .isInstanceOf(FalhaIntegracaoUsuariosException.class);
    }
}
