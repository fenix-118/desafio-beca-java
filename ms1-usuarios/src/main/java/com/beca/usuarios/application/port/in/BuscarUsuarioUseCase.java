package com.beca.usuarios.application.port.in;

import com.beca.usuarios.domain.model.Usuario;

import java.util.List;

public interface BuscarUsuarioUseCase {

    Usuario buscarPorId(Long id);

    List<Usuario> listarTodos();
}
