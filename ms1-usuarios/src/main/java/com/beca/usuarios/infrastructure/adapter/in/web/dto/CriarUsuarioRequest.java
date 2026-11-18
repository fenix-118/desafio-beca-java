package com.beca.usuarios.infrastructure.adapter.in.web.dto;

import com.beca.usuarios.domain.model.MensagensUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarUsuarioRequest(
        @NotBlank(message = MensagensUsuario.NOME_OBRIGATORIO)
        @Size(max = MensagensUsuario.NOME_MAX, message = MensagensUsuario.NOME_TAMANHO)
        String nome,

        @NotBlank(message = MensagensUsuario.EMAIL_OBRIGATORIO)
        @Email(message = MensagensUsuario.EMAIL_INVALIDO)
        @Size(max = MensagensUsuario.EMAIL_MAX, message = MensagensUsuario.EMAIL_TAMANHO)
        String email,

        @NotBlank(message = MensagensUsuario.SENHA_OBRIGATORIA)
        @Size(min = MensagensUsuario.SENHA_MIN, message = MensagensUsuario.SENHA_CURTA)
        String senha) {

    @Override
    public String toString() {
        return "CriarUsuarioRequest[nome=" + nome + ", email=" + email + ", senha=***]";
    }
}
