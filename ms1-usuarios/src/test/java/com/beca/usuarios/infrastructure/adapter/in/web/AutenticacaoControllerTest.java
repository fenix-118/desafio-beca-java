package com.beca.usuarios.infrastructure.adapter.in.web;

import com.beca.usuarios.application.port.in.AutenticarUsuarioUseCase;
import com.beca.usuarios.domain.exception.CredenciaisInvalidasException;
import com.beca.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutenticacaoController.class)
class AutenticacaoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AutenticarUsuarioUseCase autenticar;

    @Test
    void credenciaisValidasDevolvemIdNomeEEmailSemSenha() throws Exception {
        when(autenticar.autenticar("ana@beca.com", "Senha@123")).thenReturn(
                Usuario.reconstituir(3L, "Ana", "ana@beca.com", "hash-bcrypt", Instant.parse("2026-11-01T12:00:00Z")));

        mvc.perform(post("/autenticacao").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@beca.com","senha":"Senha@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@beca.com"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void credenciaisInvalidasRespondem401ComMensagemGenerica() throws Exception {
        when(autenticar.autenticar("ana@beca.com", "errada123")).thenThrow(new CredenciaisInvalidasException());

        mvc.perform(post("/autenticacao").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@beca.com","senha":"errada123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(CredenciaisInvalidasException.MENSAGEM));
    }

    @Test
    void semSenhaResponde400() throws Exception {
        mvc.perform(post("/autenticacao").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@beca.com"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
