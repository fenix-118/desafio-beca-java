package com.beca.usuarios.infrastructure.adapter.in.web;

import com.beca.usuarios.application.port.in.AtualizarUsuarioUseCase;
import com.beca.usuarios.application.port.in.BuscarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase;
import com.beca.usuarios.application.port.in.ExcluirUsuarioUseCase;
import com.beca.usuarios.domain.model.Usuario;
import com.beca.usuarios.infrastructure.adapter.in.web.seguranca.AcessoNegadoException;
import com.beca.usuarios.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada web do MS1 com o usuário autenticado simulado no atributo que o filtro preenche. */
@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

    private static final Instant CRIADO_EM = Instant.parse("2026-11-01T12:00:00Z");
    private static final String ATUALIZACAO = """
            {"nome":"Ana Souza","email":"ana@beca.com"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean private CriarUsuarioUseCase criarUsuario;
    @MockitoBean private BuscarUsuarioUseCase buscarUsuario;
    @MockitoBean private AtualizarUsuarioUseCase atualizarUsuario;
    @MockitoBean private ExcluirUsuarioUseCase excluirUsuario;

    private static Usuario ana() {
        return Usuario.reconstituir(3L, "Ana", "ana@beca.com", "hash-bcrypt", CRIADO_EM);
    }

    @Test
    void cadastroContinuaAbertoEDevolve201ComLocation() throws Exception {
        when(criarUsuario.criar(any())).thenReturn(ana());

        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Ana","email":"ana@beca.com","senha":"Senha@123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/usuarios/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void usuarioVeAPropriaConta() throws Exception {
        when(buscarUsuario.buscarPorId(3L)).thenReturn(ana());

        mvc.perform(get("/usuarios/3").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@beca.com"));
    }

    @Test
    void usuarioNaoVeContaDeOutro() throws Exception {
        mvc.perform(get("/usuarios/4").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(AcessoNegadoException.MENSAGEM));
        verifyNoInteractions(buscarUsuario);
    }

    @Test
    void ms2ComOTokenDeServicoConsultaQualquerUsuario() throws Exception {
        when(buscarUsuario.buscarPorId(4L)).thenReturn(
                Usuario.reconstituir(4L, "Bruno", "bruno@beca.com", "hash", CRIADO_EM));

        mvc.perform(get("/usuarios/4").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_SERVICO, Boolean.TRUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void semLoginResponde401() throws Exception {
        mvc.perform(get("/usuarios/3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioAtualizaAPropriaConta() throws Exception {
        when(atualizarUsuario.atualizar(eq(3L), any())).thenReturn(ana());

        mvc.perform(put("/usuarios/3").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L)
                        .contentType(MediaType.APPLICATION_JSON).content(ATUALIZACAO))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioNaoAtualizaContaDeOutro() throws Exception {
        mvc.perform(put("/usuarios/4").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L)
                        .contentType(MediaType.APPLICATION_JSON).content(ATUALIZACAO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(atualizarUsuario);
    }

    @Test
    void usuarioExcluiAPropriaConta() throws Exception {
        mvc.perform(delete("/usuarios/3").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isNoContent());
        verify(excluirUsuario).excluir(3L);
    }

    @Test
    void usuarioNaoExcluiContaDeOutro() throws Exception {
        mvc.perform(delete("/usuarios/4").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isForbidden());
        verifyNoInteractions(excluirUsuario);
    }

    @Test
    void tokenDeServicoNaoExcluiNinguem() throws Exception {
        mvc.perform(delete("/usuarios/4").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_SERVICO, Boolean.TRUE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(excluirUsuario);
    }

    @Test
    void listagemDeTodosOsUsuariosNaoExisteMais() throws Exception {
        mvc.perform(get("/usuarios").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(buscarUsuario);
    }
}
