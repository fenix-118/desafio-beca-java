package com.beca.usuarios.infrastructure.adapter.in.web;

import com.beca.usuarios.application.port.in.AtualizarUsuarioUseCase;
import com.beca.usuarios.application.port.in.BuscarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase;
import com.beca.usuarios.application.port.in.ExcluirUsuarioUseCase;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
class ApiExceptionHandlerTest {

    private static final String USUARIO_VALIDO = """
            {"nome":"Usuário Teste","email":"teste@beca.com","senha":"Senha@123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private CriarUsuarioUseCase criarUsuario;
    @MockitoBean private BuscarUsuarioUseCase buscarUsuario;
    @MockitoBean private AtualizarUsuarioUseCase atualizarUsuario;
    @MockitoBean private ExcluirUsuarioUseCase excluirUsuario;

    @Test
    void violacaoNaoReconhecidaDoBancoRetorna409ComMensagemNeutra() throws Exception {
        when(criarUsuario.criar(any()))
                .thenThrow(new DataIntegrityViolationException("Key (email)=(teste@beca.com) already exists."));

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USUARIO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ApiExceptionHandler.MENSAGEM_CONFLITO_DADOS));
    }

    @Test
    void emailDuplicadoRetorna409ComMensagemDeEmail() throws Exception {
        when(criarUsuario.criar(any())).thenThrow(new EmailJaCadastradoException());

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USUARIO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(EmailJaCadastradoException.MENSAGEM));
    }
}