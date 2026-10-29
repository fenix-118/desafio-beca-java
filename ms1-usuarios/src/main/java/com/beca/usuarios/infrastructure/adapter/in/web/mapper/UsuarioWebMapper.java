package com.beca.usuarios.infrastructure.adapter.in.web.mapper;

import com.beca.usuarios.domain.model.Usuario;
import com.beca.usuarios.infrastructure.adapter.in.web.dto.UsuarioResponse;

public final class UsuarioWebMapper {

    private UsuarioWebMapper() {
    }

    public static UsuarioResponse paraResposta(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getCriadoEm());
    }
}
