package com.beca.usuarios.infrastructure.adapter.in.web.dto;

/** Só o que o MS2 precisa para saber quem está chamando: nada de senha ou hash. */
public record UsuarioAutenticadoResponse(Long id, String nome, String email) {
}
