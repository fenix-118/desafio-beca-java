package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.ConsultarSaldoUseCase;
import com.beca.transacoes.application.port.in.ConsultarSaldoUseCase.SaldoDaConta;
import com.beca.transacoes.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O saldo é sempre o da conta do usuário do login: não há usuarioId na URL. */
@WebMvcTest(SaldoController.class)
class SaldoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarSaldoUseCase consultar;

    @Test
    void consultaOSaldoDoUsuarioDoLogin() throws Exception {
        when(consultar.consultar(3L)).thenReturn(new SaldoDaConta(3L, new BigDecimal("1938.50"), new BigDecimal("500"),
                new BigDecimal("800"), new BigDecimal("231.40"), new BigDecimal("568.60"),
                Instant.parse("2026-11-13T13:00:00Z")));

        mvc.perform(get("/contas/saldo").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(3))
                .andExpect(jsonPath("$.saldo").value(1938.5))
                .andExpect(jsonPath("$.moeda").value("BRL"))
                .andExpect(jsonPath("$.limiteDiarioDisponivel").value(568.6));
    }

    @Test
    void semLoginResponde401() throws Exception {
        mvc.perform(get("/contas/saldo"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(consultar);
    }

    @Test
    void aRotaAntigaComOIdNaUrlNaoExisteMais() throws Exception {
        mvc.perform(get("/contas/3/saldo").requestAttr(AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, 4L))
                .andExpect(status().isNotFound());
        verifyNoInteractions(consultar);
    }
}
