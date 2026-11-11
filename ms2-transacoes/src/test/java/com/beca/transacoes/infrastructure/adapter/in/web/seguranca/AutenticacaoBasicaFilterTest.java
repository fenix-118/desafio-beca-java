package com.beca.transacoes.infrastructure.adapter.in.web.seguranca;

import com.beca.transacoes.application.port.out.AutenticacaoPort;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacaoBasicaFilterTest {

    @Mock
    private AutenticacaoPort autenticacao;

    private AutenticacaoBasicaFilter filtro;
    private MockHttpServletResponse resposta;
    private MockFilterChain cadeia;

    @BeforeEach
    void setUp() {
        filtro = new AutenticacaoBasicaFilter(autenticacao, new ObjectMapper());
        resposta = new MockHttpServletResponse();
        cadeia = new MockFilterChain();
    }

    private static MockHttpServletRequest pedido(String caminho, String emailESenha) {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", caminho);
        if (emailESenha != null) {
            pedido.addHeader("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(emailESenha.getBytes(StandardCharsets.UTF_8)));
        }
        return pedido;
    }

    @Test
    void credenciaisValidasPassamComOIdDoUsuarioNaRequisicao() throws Exception {
        MockHttpServletRequest pedido = pedido("/transacoes", "ana@beca.com:Senha@123");
        when(autenticacao.autenticar("ana@beca.com", "Senha@123")).thenReturn(Optional.of(3L));

        filtro.doFilter(pedido, resposta, cadeia);

        assertThat(cadeia.getRequest()).isNotNull();
        assertThat(pedido.getAttribute(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO)).isEqualTo(3L);
    }

    @Test
    void semCabecalhoResponde401PedindoLoginBasico() throws Exception {
        filtro.doFilter(pedido("/transacoes", null), resposta, cadeia);

        assertThat(cadeia.getRequest()).isNull();
        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(resposta.getHeader("WWW-Authenticate")).startsWith("Basic");
        assertThat(resposta.getContentType()).startsWith("application/problem+json");
        assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                .contains(AutenticacaoBasicaFilter.SEM_CREDENCIAIS);
        verifyNoInteractions(autenticacao);
    }

    @Test
    void cabecalhoMalFormadoResponde401SemConsultarOMs1() throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/transacoes");
        pedido.addHeader("Authorization", "Basic !!!nao-e-base64");

        filtro.doFilter(pedido, resposta, cadeia);

        assertThat(resposta.getStatus()).isEqualTo(401);
        verifyNoInteractions(autenticacao);
    }

    @Test
    void credenciaisInvalidasRespondem401() throws Exception {
        when(autenticacao.autenticar("ana@beca.com", "errada123")).thenReturn(Optional.empty());

        filtro.doFilter(pedido("/transacoes", "ana@beca.com:errada123"), resposta, cadeia);

        assertThat(cadeia.getRequest()).isNull();
        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                .contains(AutenticacaoBasicaFilter.CREDENCIAIS_INVALIDAS)
                .doesNotContain("errada123");
    }

    @Test
    void ms1ForaDoArResponde503() throws Exception {
        when(autenticacao.autenticar("ana@beca.com", "Senha@123"))
                .thenThrow(new ServicoUsuariosIndisponivelException("MS1 fora", null));

        filtro.doFilter(pedido("/transacoes", "ana@beca.com:Senha@123"), resposta, cadeia);

        assertThat(cadeia.getRequest()).isNull();
        assertThat(resposta.getStatus()).isEqualTo(503);
    }

    @Test
    void documentacaoEHealthFicamAbertos() throws Exception {
        for (String aberta : new String[]{"/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs",
                "/v3/api-docs/swagger-config", "/actuator/health"}) {
            MockFilterChain cadeiaAberta = new MockFilterChain();
            filtro.doFilter(pedido(aberta, null), new MockHttpServletResponse(), cadeiaAberta);
            assertThat(cadeiaAberta.getRequest()).as(aberta).isNotNull();
        }
        verifyNoInteractions(autenticacao);
    }
}
