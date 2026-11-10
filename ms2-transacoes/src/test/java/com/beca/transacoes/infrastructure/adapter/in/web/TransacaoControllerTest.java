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

        mvc.perform(get("/transacoes/18"))
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
}
