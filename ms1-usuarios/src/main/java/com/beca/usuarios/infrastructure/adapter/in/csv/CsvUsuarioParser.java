package com.beca.usuarios.infrastructure.adapter.in.csv;

import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.LinhaImportacao;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class CsvUsuarioParser {

    static final int MAX_LINHAS = 500;
    private static final List<String> COLUNAS_OBRIGATORIAS = List.of("nome", "email", "senha");

    public List<LinhaImportacao> ler(InputStream entrada) {
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            ignorarBom(leitor);
            char delimitador = detectarDelimitador(leitor);

            CSVFormat formato = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimitador)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build();

            try (CSVParser parser = formato.parse(leitor)) {
                validarCabecalho(parser.getHeaderNames());

                List<LinhaImportacao> linhas = new ArrayList<>();
                int numeroLinha = 1; // o cabeçalho é a linha 1
                for (CSVRecord registro : parser) {
                    numeroLinha++;
                    if (linhas.size() >= MAX_LINHAS) {
                        throw new ArquivoCsvInvalidoException(
                                "O arquivo excede o limite de " + MAX_LINHAS + " linhas de dados.");
                    }
                    linhas.add(new LinhaImportacao(
                            numeroLinha,
                            valor(registro, "nome"),
                            valor(registro, "email"),
                            valor(registro, "senha")));
                }
                if (linhas.isEmpty()) {
                    throw new ArquivoCsvInvalidoException("O arquivo não possui linhas de dados.");
                }
                return linhas;
            }
        } catch (IOException | UncheckedIOException | IllegalStateException | IllegalArgumentException e) {
            // A mensagem original do parser pode conter trechos do arquivo: não a repassamos.
            throw new ArquivoCsvInvalidoException("Não foi possível ler o arquivo CSV. Verifique o formato.");
        }
    }

    /** Arquivos UTF-8 salvos pelo Excel começam com um marcador invisível (BOM). */
    private void ignorarBom(BufferedReader leitor) throws IOException {
        leitor.mark(1);
        if (leitor.read() != 0xFEFF) {
            leitor.reset();
        }
    }

    /** O Excel em português costuma salvar com ponto e vírgula; aceitamos os dois. */
    private char detectarDelimitador(BufferedReader leitor) throws IOException {
        leitor.mark(8192);
        String primeiraLinha = leitor.readLine();
        leitor.reset();
        if (primeiraLinha == null || primeiraLinha.isBlank()) {
            throw new ArquivoCsvInvalidoException("O arquivo está vazio.");
        }
        return primeiraLinha.contains(";") && !primeiraLinha.contains(",") ? ';' : ',';
    }

    private void validarCabecalho(List<String> cabecalho) {
        List<String> colunas = cabecalho.stream()
                .map(c -> c == null ? "" : c.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!colunas.containsAll(COLUNAS_OBRIGATORIAS)) {
            throw new ArquivoCsvInvalidoException(
                    "Cabeçalho inválido. O arquivo deve ter as colunas: nome, email, senha.");
        }
    }

    private static String valor(CSVRecord registro, String coluna) {
        return registro.isSet(coluna) ? registro.get(coluna) : null;
    }
}
