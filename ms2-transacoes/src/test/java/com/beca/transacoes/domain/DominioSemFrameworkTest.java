package com.beca.transacoes.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critério de aceite da tarefa 1.1: o pacote de domínio não importa Spring, JPA nem HTTP.
 * Se alguém colocar uma anotação de framework no domínio, este teste quebra.
 */
class DominioSemFrameworkTest {

    private static final Path DOMINIO = Path.of("src/main/java/com/beca/transacoes/domain");

    private static final List<String> PROIBIDOS = List.of(
            "import org.springframework",
            "import jakarta.persistence",
            "import javax.persistence",
            "import org.hibernate",
            "import jakarta.servlet",
            "import java.net.http",
            "import com.fasterxml");

    @Test
    void dominioNaoDependeDeFramework() throws IOException {
        try (Stream<Path> arquivos = Files.walk(DOMINIO)) {
            List<String> violacoes = arquivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(DominioSemFrameworkTest::importsProibidos)
                    .toList();

            assertThat(violacoes).isEmpty();
        }
    }

    private static Stream<String> importsProibidos(Path arquivo) {
        try {
            return Files.readAllLines(arquivo).stream()
                    .map(String::trim)
                    .filter(linha -> PROIBIDOS.stream().anyMatch(linha::startsWith))
                    .map(linha -> arquivo.getFileName() + ": " + linha);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
