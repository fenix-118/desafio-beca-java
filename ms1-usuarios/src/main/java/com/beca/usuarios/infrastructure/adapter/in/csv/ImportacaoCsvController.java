package com.beca.usuarios.infrastructure.adapter.in.csv;

import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase;
import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.LinhaImportacao;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/usuarios/importacao")
public class ImportacaoCsvController {

    private final ImportarUsuariosUseCase importarUsuarios;
    private final CsvUsuarioParser parser;

    public ImportacaoCsvController(ImportarUsuariosUseCase importarUsuarios, CsvUsuarioParser parser) {
        this.importarUsuarios = importarUsuarios;
        this.parser = parser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoResponse importar(@RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new ArquivoCsvInvalidoException("O arquivo está vazio.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome == null || !nome.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ArquivoCsvInvalidoException("Envie um arquivo com extensão .csv.");
        }

        List<LinhaImportacao> linhas;
        try (InputStream entrada = arquivo.getInputStream()) {
            linhas = parser.ler(entrada);
        } catch (IOException e) {
            throw new ArquivoCsvInvalidoException("Não foi possível ler o arquivo enviado.");
        }
        return ImportacaoResponse.de(importarUsuarios.importar(linhas));
    }
}
