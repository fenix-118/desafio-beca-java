package com.beca.usuarios.application.port.in;

import com.beca.usuarios.domain.model.Usuario;

public interface CriarUsuarioUseCase {

    Usuario criar(CriarUsuarioCommand comando);

    record CriarUsuarioCommand(String nome, String email, String senha) {
        @Override
        public String toString() {
            return "CriarUsuarioCommand[nome=" + nome + ", email=" + email + ", senha=***]";
        }
    }
}
