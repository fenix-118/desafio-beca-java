package com.beca.usuarios.infrastructure.adapter.in.csv;

import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.LinhaImportacao;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvUsuarioParserTest {

    private final CsvUsuarioParser parser = new CsvUsuarioParser();

    private static InputStream csv(String conteudo) {
        return new ByteArrayInputStream(conteudo.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- leitura válida ----------

    @Test
    void deveLerCsvSeparadoPorVirgula() {
        List<LinhaImportacao> linhas = parser.ler(csv("""
                nome,email,senha
                Ana,ana@beca.com,Senha@123
                Bruno,bruno@beca.com,Senha@456
                """));

        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0)).isEqualTo(new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"));
        assertThat(linhas.get(1)).isEqualTo(new LinhaImportacao(3, "Bruno", "bruno@beca.com", "Senha@456"));
    }

    @Test
    void deveLerCsvSeparadoPorPontoEVirgulaComoNoExcelEmPortugues() {
        List<LinhaImportacao> linhas = parser.ler(csv("""
                nome;email;senha
                Ana;ana@beca.com;Senha@123
                """));

        assertThat(linhas).containsExactly(new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"));
    }

    @Test
    void deveIgnorarMarcadorBomDoExcel() {
        List<LinhaImportacao> linhas = parser.ler(csv("\uFEFFnome,email,senha\nAna,ana@beca.com,Senha@123\n"));

        assertThat(linhas).containsExactly(new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"));
    }

    @Test
    void deveAceitarCabecalhoEmMaiusculasEColunasForaDeOrdem() {
        List<LinhaImportacao> linhas = parser.ler(csv("""
                SENHA,Email,NOME
                Senha@123,ana@beca.com,Ana
                """));

        assertThat(linhas).containsExactly(new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"));
    }

    @Test
    void deveRemoverEspacosDosValores() {
        List<LinhaImportacao> linhas = parser.ler(csv("""
                nome,email,senha
                  Ana  ,  ana@beca.com  ,  Senha@123
                """));

        assertThat(linhas).containsExactly(new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"));
    }

    @Test
    void deveIgnorarLinhasEmBrancoNoFinal() {
        List<LinhaImportacao> linhas = parser.ler(csv("nome,email,senha\nAna,ana@beca.com,Senha@123\n\n\n"));

        assertThat(linhas).hasSize(1);
    }

    @Test
    void deveAceitarExatamenteOLimiteDeLinhas() {
        List<LinhaImportacao> linhas = parser.ler(csv(gerarCsv(CsvUsuarioParser.MAX_LINHAS)));

        assertThat(linhas).hasSize(CsvUsuarioParser.MAX_LINHAS);
    }

    // ---------- arquivos inválidos ----------

    @Test
    void deveRejeitarArquivoVazio() {
        assertThrows(ArquivoCsvInvalidoException.class, () -> parser.ler(csv("")));
    }

    @Test
    void deveRejeitarArquivoSomenteComCabecalho() {
        assertThrows(ArquivoCsvInvalidoException.class, () -> parser.ler(csv("nome,email,senha\n")));
    }

    @Test
    void deveRejeitarCabecalhoSemColunaObrigatoria() {
        assertThrows(ArquivoCsvInvalidoException.class, () -> parser.ler(csv("""
                nome,email
                Ana,ana@beca.com
                """)));
    }

    @Test
    void deveRejeitarArquivoAcimaDoLimiteDeLinhas() {
        assertThrows(ArquivoCsvInvalidoException.class,
                () -> parser.ler(csv(gerarCsv(CsvUsuarioParser.MAX_LINHAS + 1))));
    }

    private static String gerarCsv(int quantidadeDeLinhas) {
        StringBuilder conteudo = new StringBuilder("nome,email,senha\n");
        for (int i = 1; i <= quantidadeDeLinhas; i++) {
            conteudo.append("Usuario").append(i)
                    .append(",usuario").append(i).append("@beca.com")
                    .append(",Senha@123\n");
        }
        return conteudo.toString();
    }
}