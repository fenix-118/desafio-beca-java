package com.beca.usuarios.application.port.in;

import java.util.List;

public interface ImportarUsuariosUseCase {

    ResultadoImportacao importar(List<LinhaImportacao> linhas);

    /** numeroLinha: posição no arquivo, contando o cabeçalho como linha 1. */
    record LinhaImportacao(int numeroLinha, String nome, String email, String senha) {
        @Override
        public String toString() {
            return "LinhaImportacao[numeroLinha=" + numeroLinha + ", nome=" + nome
                    + ", email=" + email + ", senha=***]";
        }
    }

    record FalhaImportacao(int numeroLinha, String motivo) {
    }

    record ResultadoImportacao(int total, int criados, List<FalhaImportacao> falhas) {
    }
}
