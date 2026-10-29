package com.beca.usuarios.infrastructure.adapter.in.web.dto;

import java.time.Instant;

public record UsuarioResponse(Long id, String nome, String email, Instant criadoEm) {
}
