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
import com.beca.transacoes.infrastructure.adapter.in.web.seguranca.AcessoNegadoException;
import com.beca.transacoes.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        mvc.perform(post("/transacoes").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 9999L).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 9999, \"tipo\": \"DEPOSITO\", \"valor\": 10 }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuário 9999 não encontrado."));
    }

    @Test
    void ms1ForaDoArAoConferirODonoResponde503() throws Exception {
        when(registrar.registrar(any())).thenThrow(new ServicoUsuariosIndisponivelException("timeout", null));

        mvc.perform(post("/transacoes").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 3, \"tipo\": \"DEPOSITO\", \"valor\": 10 }"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(ApiExceptionHandler.USUARIOS_INDISPONIVEL));
    }

    @Test
    void erroInesperadoDoMs1Responde502() throws Exception {
        when(registrar.registrar(any())).thenThrow(new FalhaIntegracaoUsuariosException(403, null));

        mvc.perform(post("/transacoes").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L).contentType(MediaType.APPLICATION_JSON)
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

        mvc.perform(post("/transacoes").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L).contentType(MediaType.APPLICATION_JSON).content(TRANSFERENCIA))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/transacoes/7"))
                .andExpect(jsonPath("$.status").value("REJEITADA"))
                .andExpect(jsonPath("$.motivoRejeicao").value("DESTINO_INEXISTENTE"))
                .andExpect(jsonPath("$.motivoRejeicaoDescricao").value("Usuário de destino não encontrado."));
    }

    @Test
    void corpoInvalidoResponde400ComOCampo() throws Exception {
        mvc.perform(post("/transacoes").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 3, \"tipo\": \"DEPOSITO\", \"valor\": 10.555 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.valor").exists());
    }

    @Test
    void consultaMostraOValorEmRealEEmUsdEEurComTaxaEData() throws Exception {
        Transacao compra = Transacao.reconstituir(18L, 3L, TipoTransacao.COMPRA, new BigDecimal("120.00"),
                Categoria.ALIMENTACAO, null, null, Instant.parse("2026-11-10T14:39:13Z"),
                StatusTransacao.APROVADA, null, java.util.List.of(
                        new com.beca.transacoes.domain.model.Cotacao(com.beca.transacoes.domain.model.MoedaEstrangeira.USD,
                                new BigDecimal("5.5146"), java.time.LocalDate.of(2026, 11, 9)),
                        new com.beca.transacoes.domain.model.Cotacao(com.beca.transacoes.domain.model.MoedaEstrangeira.EUR,
                                new BigDecimal("6.1317"), java.time.LocalDate.of(2026, 11, 6))));
        when(buscar.buscarPorId(18L)).thenReturn(compra);

        mvc.perform(get("/transacoes/18").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(120.0))
                .andExpect(jsonPath("$.moeda").value("BRL"))
                .andExpect(jsonPath("$.conversoes[0].moeda").value("USD"))
                .andExpect(jsonPath("$.conversoes[0].disponivel").value(true))
                .andExpect(jsonPath("$.conversoes[0].valor").value(21.76))
                .andExpect(jsonPath("$.conversoes[0].taxa").value(5.5146))
                .andExpect(jsonPath("$.conversoes[0].dataCotacao").value("2026-11-09"))
                .andExpect(jsonPath("$.conversoes[1].moeda").value("EUR"))
                .andExpect(jsonPath("$.conversoes[1].valor").value(19.57))
                .andExpect(jsonPath("$.conversoes[1].dataCotacao").value("2026-11-06"));
    }

    @Test
    void semCotacaoAConsultaRespondeComAConversaoIndisponivel() throws Exception {
        Transacao semCambio = Transacao.reconstituir(21L, 3L, TipoTransacao.COMPRA, new BigDecimal("10.00"),
                Categoria.ALIMENTACAO, null, null, Instant.parse("2026-11-10T18:33:22Z"),
                StatusTransacao.APROVADA, null);
        when(buscar.buscarPorId(21L)).thenReturn(semCambio);

        mvc.perform(get("/transacoes/21").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.conversoes[0].moeda").value("USD"))
                .andExpect(jsonPath("$.conversoes[0].disponivel").value(false))
                .andExpect(jsonPath("$.conversoes[0].valor").doesNotExist())
                .andExpect(jsonPath("$.conversoes[1].disponivel").value(false));
    }

    // ---------- autorização (tarefa 6.3) ----------

    @Test
    void registrarParaOutroUsuarioResponde403() throws Exception {
        mvc.perform(post("/transacoes").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 4L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"usuarioId\": 3, \"tipo\": \"DEPOSITO\", \"valor\": 10 }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(AcessoNegadoException.MENSAGEM));
        org.mockito.Mockito.verifyNoInteractions(registrar);
    }

    @Test
    void consultarTransacaoDeOutroUsuarioResponde403() throws Exception {
        Transacao daAna = Transacao.reconstituir(18L, 3L, TipoTransacao.COMPRA, new BigDecimal("120.00"),
                Categoria.ALIMENTACAO, null, null, Instant.parse("2026-11-10T14:39:13Z"),
                StatusTransacao.APROVADA, null);
        when(buscar.buscarPorId(18L)).thenReturn(daAna);

        mvc.perform(get("/transacoes/18").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 4L))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarTransacoesDeOutroUsuarioResponde403() throws Exception {
        mvc.perform(get("/transacoes").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 4L)
                        .param("usuarioId", "3"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(listar);
    }

    @Test
    void semUsuarioAutenticadoResponde401() throws Exception {
        mvc.perform(get("/transacoes").param("usuarioId", "3"))
                .andExpect(status().isUnauthorized());
    }
}
