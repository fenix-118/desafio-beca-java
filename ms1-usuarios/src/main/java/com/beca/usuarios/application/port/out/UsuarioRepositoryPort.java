package com.beca.usuarios.application.port.out;

import com.beca.usuarios.domain.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositoryPort {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorId(Long id);

    List<Usuario> listarTodos();

    boolean existePorEmail(String email);

    boolean existePorId(Long id);

    void excluirPorId(Long id);
}
