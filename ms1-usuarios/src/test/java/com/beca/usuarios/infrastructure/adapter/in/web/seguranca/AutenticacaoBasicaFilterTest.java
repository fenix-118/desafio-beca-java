package com.beca.usuarios.infrastructure.adapter.in.web.seguranca;

import com.beca.usuarios.application.port.in.AutenticarUsuarioUseCase;
import com.beca.usuarios.domain.exception.CredenciaisInvalidasException;
import com.beca.usuarios.domain.model.Usuario;
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
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacaoBasicaFilterTest {

    private static final String TOKEN = "token-de-servico-de-teste";

    @Mock
    private AutenticarUsuarioUseCase autenticar;

    private AutenticacaoBasicaFilter filtro;
    private MockHttpServletResponse resposta;
    private MockFilterChain cadeia;

    @BeforeEach
    void setUp() {
        filtro = new AutenticacaoBasicaFilter(autenticar, TOKEN, new ObjectMapper());
        resposta = new MockHttpServletResponse();
        cadeia = new MockFilterChain();
    }

    private static MockHttpServletRequest pedido(String metodo, String caminho) {
        return new MockHttpServletRequest(metodo, caminho);
    }

    private static String basic(String emailESenha) {
        return "Basic " + Base64.getEncoder().encodeToString(emailESenha.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void loginValidoPassaComOIdDoUsuarioNaRequisicao() throws Exception {
        MockHttpServletRequest pedido = pedido("PUT", "/usuarios/3");
        pedido.addHeader("Authorization", basic("ana@beca.com:Senha@123"));
        when(autenticar.autenticar("ana@beca.com", "Senha@123")).thenReturn(
                Usuario.reconstituir(3L, "Ana", "ana@beca.com", "hash", Instant.parse("2026-11-01T12:00:00Z")));

        filtro.doFilter(pedido, resposta, cadeia);

        assertThat(cadeia.getRequest()).isNotNull();
        assertThat(pedido.getAttribute(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO)).isEqualTo(3L);
    }

    @Test
    void semLoginResponde401() throws Exception {
        filtro.doFilter(pedido("DELETE", "/usuarios/3"), resposta, cadeia);

        assertThat(cadeia.getRequest()).isNull();
        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(resposta.getHeader("WWW-Authenticate")).startsWith("Basic");
        verifyNoInteractions(autenticar);
    }

    @Test
    void senhaErradaResponde401ComAMensagemGenerica() throws Exception {
        MockHttpServletRequest pedido = pedido("GET", "/usuarios/3");
        pedido.addHeader("Authorization", basic("ana@beca.com:errada123"));
        when(autenticar.autenticar("ana@beca.com", "errada123")).thenThrow(new CredenciaisInvalidasException());

        filtro.doFilter(pedido, resposta, cadeia);

        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                .contains(CredenciaisInvalidasException.MENSAGEM)
                .doesNotContain("errada123");
    }

    @Test
    void tokenDeServicoLiberaSoOGet() throws Exception {
        MockHttpServletRequest get = pedido("GET", "/usuarios/4");
        get.addHeader(AutenticacaoBasicaFilter.CABECALHO_SERVICO, TOKEN);

        filtro.doFilter(get, resposta, cadeia);

        assertThat(cadeia.getRequest()).isNotNull();
        assertThat(get.getAttribute(AutenticacaoBasicaFilter.ATRIBUTO_SERVICO)).isEqualTo(Boolean.TRUE);

        MockHttpServletRequest delete = pedido("DELETE", "/usuarios/4");
        delete.addHeader(AutenticacaoBasicaFilter.CABECALHO_SERVICO, TOKEN);
        MockHttpServletResponse respostaDelete = new MockHttpServletResponse();
        MockFilterChain cadeiaDelete = new MockFilterChain();

        filtro.doFilter(delete, respostaDelete, cadeiaDelete);

        assertThat(cadeiaDelete.getRequest()).isNull();
        assertThat(respostaDelete.getStatus()).isEqualTo(401);
        verifyNoInteractions(autenticar);
    }

    @Test
    void tokenErradoNaoLiberaNada() throws Exception {
        MockHttpServletRequest pedido = pedido("GET", "/usuarios/4");
        pedido.addHeader(AutenticacaoBasicaFilter.CABECALHO_SERVICO, "outro-token");

        filtro.doFilter(pedido, resposta, cadeia);

        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(pedido.getAttribute(AutenticacaoBasicaFilter.ATRIBUTO_SERVICO)).isNull();
    }

    @Test
    void cadastroImportacaoELoginFicamAbertos() throws Exception {
        for (MockHttpServletRequest aberto : new MockHttpServletRequest[]{
                pedido("POST", "/usuarios"), pedido("POST", "/usuarios/importacao"),
                pedido("POST", "/autenticacao"), pedido("GET", "/swagger-ui/index.html"),
                pedido("GET", "/actuator/health")}) {
            MockFilterChain cadeiaAberta = new MockFilterChain();
            filtro.doFilter(aberto, new MockHttpServletResponse(), cadeiaAberta);
            assertThat(cadeiaAberta.getRequest()).as(aberto.getRequestURI()).isNotNull();
        }
        verifyNoInteractions(autenticar);
    }
}
