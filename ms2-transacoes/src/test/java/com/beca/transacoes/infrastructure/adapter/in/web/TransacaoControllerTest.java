package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.BuscarTransacaoUseCase;
import com.beca.transacoes.application.port.in.ListarTransacoesUseCase;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase;
import com.beca.transacoes.application.port.out.FalhaIntegracaoUsuariosException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.beca.transacoes.domain.exception.UsuarioNaoEncontradoException;
import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.MotivoRejeicao;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sobe só a camada web (controller + ApiExceptionHandler), com os casos de uso simulados. */
@WebMvcTest(TransacaoController.class)
class TransacaoControllerTest {

    private static final String TRANSFERENCIA = """
            { "usuarioId": 3, "tipo": "TRANSFERENCIA", "valor": 10, "categoria": "OUTROS", "usuarioDestinoId": 9999 }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegistrarTransacaoUseCase registrar;

    @MockitoBean
    private BuscarTransacaoUseCase buscar;

    @MockitoBean
    private ListarTransacoesUseCase listar;

    @Test
    void donoInexistenteResponde404() throws Exception {
        when(registrar.registrar(any())).thenThrow(new UsuarioNaoEncontradoException(9999L));

        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 9999, \"tipo\": \"DEPOSITO\", \"valor\": 10 }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuário 9999 não encontrado."));
    }

    @Test
    void ms1ForaDoArAoConferirODonoResponde503() throws Exception {
        when(registrar.registrar(any())).thenThrow(new ServicoUsuariosIndisponivelException("timeout", null));

        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 3, \"tipo\": \"DEPOSITO\", \"valor\": 10 }"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(ApiExceptionHandler.USUARIOS_INDISPONIVEL));
    }

    @Test
    void erroInesperadoDoMs1Responde502() throws Exception {
        when(registrar.registrar(any())).thenThrow(new FalhaIntegracaoUsuariosException(403, null));

        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 3, \"tipo\": \"DEPOSITO\", \"valor\": 10 }"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(ApiExceptionHandler.FALHA_INTEGRACAO_USUARIOS));
    }

    @Test
    void transferenciaRejeitadaResponde201ComMotivoELocation() throws Exception {
        Transacao rejeitada = Transacao.reconstituir(7L, 3L, TipoTransacao.TRANSFERENCIA, new BigDecimal("10.00"),
                Categoria.OUTROS, 9999L, null, Instant.parse("2026-11-10T13:00:00Z"),
                StatusTransacao.REJEITADA, MotivoRejeicao.DESTINO_INEXISTENTE);
        when(registrar.registrar(any())).thenReturn(rejeitada);

        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content(TRANSFERENCIA))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/transacoes/7"))
                .andExpect(jsonPath("$.status").value("REJEITADA"))
                .andExpect(jsonPath("$.motivoRejeicao").value("DESTINO_INEXISTENTE"))
                .andExpect(jsonPath("$.motivoRejeicaoDescricao").value("Usuário de destino não encontrado."));
    }

    @Test
    void corpoInvalidoResponde400ComOCampo() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 3, \"tipo\": \"DEPOSITO\", \"valor\": 10.555 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.valor").exists());
    }
}
