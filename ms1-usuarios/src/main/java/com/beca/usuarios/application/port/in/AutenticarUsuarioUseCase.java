package com.beca.usuarios.application.port.in;

import com.beca.usuarios.domain.model.Usuario;

public interface AutenticarUsuarioUseCase {

    /**
     * Confere o e-mail e a senha (tarefa 6.1).
     *
     * @return o usuário, se a senha confere
     * @throws com.beca.usuarios.domain.exception.CredenciaisInvalidasException se o e-mail não existe ou a senha não confere
     */
    Usuario autenticar(String email, String senha);
}
