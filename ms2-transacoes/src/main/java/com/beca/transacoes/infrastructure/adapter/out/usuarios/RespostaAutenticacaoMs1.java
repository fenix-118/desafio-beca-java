package com.beca.transacoes.infrastructure.adapter.out.usuarios;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resposta do POST /autenticacao do MS1: o MS2 só precisa do id. */
@JsonIgnoreProperties(ignoreUnknown = true)
record RespostaAutenticacaoMs1(Long id) {
}
