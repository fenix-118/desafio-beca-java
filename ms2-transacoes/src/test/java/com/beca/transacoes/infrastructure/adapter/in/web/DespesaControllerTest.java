package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase;
import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase.ConsultarDespesasQuery;
import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase.ResumoDoPeriodo;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.ResumoDespesas;
import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sobe só a camada web (controller + ApiExceptionHandler), com o caso de uso simulado. */
@WebMvcTest(DespesaController.class)
class DespesaControllerTest {

    private static final Instant QUANDO = Instant.parse("2026-11-10T15:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarDespesasUseCase consultar;

    private static Transacao compra(String valor, Categoria categoria) {
        return Transacao.reconstituir(1L, 3L, TipoTransacao.COMPRA, new BigDecimal(valor), categoria, null, null,
                QUANDO, StatusTransacao.APROVADA, null);
    }

    @Test
    void devolveOResumoPorCategoriaComOTotalGeral() throws Exception {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 30);
        ResumoDespesas resumo = ResumoDespesas.calcular(List.of(
                compra("100.00", Categoria.MORADIA), compra("50.00", Categoria.ALIMENTACAO)));
        when(consultar.consultar(new ConsultarDespesasQuery(3L, inicio, fim)))
                .thenReturn(new ResumoDoPeriodo(3L, inicio, fim, resumo));

        mvc.perform(get("/despesas/resumo").param("usuarioId", "3")
                        .param("inicio", "2026-11-01").param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(3))
                .andExpect(jsonPath("$.inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.fim").value("2026-11-30"))
                .andExpect(jsonPath("$.moeda").value("BRL"))
                .andExpect(jsonPath("$.totalGeral").value(150.0))
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.porCategoria[0].categoria").value("MORADIA"))
                .andExpect(jsonPath("$.porCategoria[0].total").value(100.0))
                .andExpect(jsonPath("$.porCategoria[1].categoria").value("ALIMENTACAO"));
    }

    @Test
    void periodoInvalidoResponde400() throws Exception {
        when(consultar.consultar(any())).thenThrow(new DadosInvalidosException("A data 'inicio' deve ser anterior ou igual à data 'fim'."));

        mvc.perform(get("/despesas/resumo").param("usuarioId", "3")
                        .param("inicio", "2026-11-30").param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semUsuarioResponde400() throws Exception {
        mvc.perform(get("/despesas/resumo"))
                .andExpect(status().isBadRequest());
    }
}
