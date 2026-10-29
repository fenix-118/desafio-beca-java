package com.beca.usuarios.infrastructure.adapter.in.csv;

import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.ResultadoImportacao;

import java.util.List;

public record ImportacaoResponse(int total, int criados, int falharam, List<Falha> falhas) {

    public record Falha(int linha, String motivo) {
    }

    public static ImportacaoResponse de(ResultadoImportacao resultado) {
        List<Falha> falhas = resultado.falhas().stream()
                .map(f -> new Falha(f.numeroLinha(), f.motivo()))
                .toList();
        return new ImportacaoResponse(resultado.total(), resultado.criados(), falhas.size(), falhas);
    }
}
