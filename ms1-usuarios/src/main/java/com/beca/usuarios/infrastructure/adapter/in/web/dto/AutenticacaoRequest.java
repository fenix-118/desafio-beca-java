package com.beca.usuarios.infrastructure.adapter.in.web.dto;

import com.beca.usuarios.domain.model.MensagensUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AutenticacaoRequest(
        @Schema(example = "ana.souza@example.com")
        @NotBlank(message = MensagensUsuario.EMAIL_OBRIGATORIO)
        String email,

        @Schema(example = "Exemplo#2026a")
        @NotBlank(message = MensagensUsuario.SENHA_OBRIGATORIA)
        String senha) {

    /** A senha nunca aparece em log nem em mensagem de erro. */
    @Override
    public String toString() {
        return "AutenticacaoRequest[email=" + email + ", senha=***]";
    }
}
