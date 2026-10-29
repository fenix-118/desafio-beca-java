package com.beca.usuarios.application.port.in;

import com.beca.usuarios.domain.model.Usuario;

public interface AtualizarUsuarioUseCase {

    Usuario atualizar(Long id, AtualizarUsuarioCommand comando);

    /** A senha é opcional: se vier nula ou vazia, a senha atual é mantida. */
    record AtualizarUsuarioCommand(String nome, String email, String senha) {
        @Override
        public String toString() {
            return "AtualizarUsuarioCommand[nome=" + nome + ", email=" + email + ", senha=***]";
        }
    }
}
