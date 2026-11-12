package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.GerarRelatorioUseCase;
import com.beca.transacoes.application.port.in.GerarRelatorioUseCase.GerarRelatorioQuery;
import com.beca.transacoes.application.port.in.GerarRelatorioUseCase.RelatorioDoPeriodo;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import com.beca.transacoes.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sobe só a camada web (controller + ApiExceptionHandler), com o caso de uso simulado. */
@WebMvcTest(RelatorioController.class)
class RelatorioControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GerarRelatorioUseCase gerar;

    @Test
    void devolveOCsvDoUsuarioDoLoginComoDownload() throws Exception {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 30);
        Transacao compra = Transacao.reconstituir(7L, 3L, TipoTransacao.COMPRA, new BigDecimal("80.50"),
                Categoria.LAZER, null, "Cinema", Instant.parse("2026-11-10T15:00:00Z"), StatusTransacao.APROVADA, null);
        when(gerar.gerar(new GerarRelatorioQuery(3L, inicio, fim)))
                .thenReturn(new RelatorioDoPeriodo(3L, inicio, fim, List.of(compra)));

        byte[] corpo = mvc.perform(get("/transacoes/relatorio")
                        .requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L)
                        .param("inicio", "2026-11-01").param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"transacoes-2026-11-01-a-2026-11-30.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(corpo, StandardCharsets.UTF_8);
        assertThat(csv).contains("Cinema").contains("COMPRA").contains("APROVADA");
    }

    @Test
    void semLoginResponde401SemGerarORelatorio() throws Exception {
        mvc.perform(get("/transacoes/relatorio"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(gerar);
    }

    @Test
    void periodoInvalidoResponde400ComProblemDetail() throws Exception {
        when(gerar.gerar(any())).thenThrow(new DadosInvalidosException("O período pode ter no máximo 366 dias."));

        mvc.perform(get("/transacoes/relatorio").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L)
                        .param("inicio", "2025-01-01").param("fim", "2026-11-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O período pode ter no máximo 366 dias."));
    }
}
