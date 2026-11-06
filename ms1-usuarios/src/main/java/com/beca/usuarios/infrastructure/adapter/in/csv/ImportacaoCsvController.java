package com.beca.usuarios.infrastructure.adapter.in.csv;

import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase;
import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.LinhaImportacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Tag(name = "Importação CSV", description = "Cadastro de usuários em massa a partir de arquivo CSV")
@RestController
@RequestMapping("/usuarios/importacao")
public class ImportacaoCsvController {

    private final ImportarUsuariosUseCase importarUsuarios;
    private final CsvUsuarioParser parser;

    public ImportacaoCsvController(ImportarUsuariosUseCase importarUsuarios, CsvUsuarioParser parser) {
        this.importarUsuarios = importarUsuarios;
        this.parser = parser;
    }

    @Operation(summary = "Importa usuários a partir de um arquivo CSV",
            description = "Recebe um arquivo CSV (máximo de 1 MB) e retorna um relatório por linha com os usuários importados e os erros. "
                    + "Retorna 400 se o arquivo for inválido e 413 se exceder o tamanho máximo.")
    @ApiResponse(responseCode = "200", description = "Relatório da importação, com usuários criados e falhas por linha")
    @ApiResponse(responseCode = "400", description = "Arquivo vazio, sem extensão .csv ou ilegível",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "Arquivo maior que 1 MB",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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