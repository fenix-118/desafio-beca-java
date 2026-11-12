package com.beca.usuarios.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AutenticacaoRequest(
        @Schema(example = "ana.souza@example.com")
        @NotBlank(message = "O e-mail é obrigatório")
        String email,

        @Schema(example = "Exemplo#2026a")
        @NotBlank(message = "A senha é obrigatória")
        String senha) {

    /** A senha nunca aparece em log nem em mensagem de erro. */
    @Override
    public String toString() {
        return "AutenticacaoRequest[email=" + email + ", senha=***]";
    }
}
