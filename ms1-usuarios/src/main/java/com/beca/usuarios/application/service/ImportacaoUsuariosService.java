package com.beca.usuarios.application.service;

import com.beca.usuarios.application.port.in.CriarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase.CriarUsuarioCommand;
import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase;
import com.beca.usuarios.domain.exception.DadosInvalidosException;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;

import java.util.ArrayList;
import java.util.List;

public class ImportacaoUsuariosService implements ImportarUsuariosUseCase {

    private final CriarUsuarioUseCase criarUsuario;

    public ImportacaoUsuariosService(CriarUsuarioUseCase criarUsuario) {
        this.criarUsuario = criarUsuario;
    }

    @Override
    public ResultadoImportacao importar(List<LinhaImportacao> linhas) {
        List<FalhaImportacao> falhas = new ArrayList<>();
        int criados = 0;

        for (LinhaImportacao linha : linhas) {
            try {
                criarUsuario.criar(new CriarUsuarioCommand(linha.nome(), linha.email(), linha.senha()));
                criados++;
            } catch (DadosInvalidosException | EmailJaCadastradoException e) {
                falhas.add(new FalhaImportacao(linha.numeroLinha(), e.getMessage()));
            }
        }
        return new ResultadoImportacao(linhas.size(), criados, falhas);
    }
}
