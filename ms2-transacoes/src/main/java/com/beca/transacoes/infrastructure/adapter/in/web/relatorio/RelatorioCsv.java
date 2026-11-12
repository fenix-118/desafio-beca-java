package com.beca.transacoes.infrastructure.adapter.in.web.relatorio;

import com.beca.transacoes.domain.model.Transacao;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Escreve o relatório de transações em CSV (pedido 7). Fica no adaptador web: é formato de saída.
 *
 * Feito para abrir certo com dois cliques no Excel em português (BJ-33):
 * <ul>
 *   <li>BOM UTF-8 no início, para o Excel não trocar acentos por símbolos;</li>
 *   <li>";" como separador, porque no Brasil a vírgula é o separador decimal;</li>
 *   <li>valor com vírgula decimal e sem separador de milhar (1500,00), lido como número;</li>
 *   <li>data no formato dd/MM/aaaa HH:mm, no fuso de São Paulo, lida como data;</li>
 *   <li>texto que começa com = + - @ (ou tab/CR) ganha um apóstrofo na frente, para o Excel
 *       não executar como fórmula (injeção de CSV, OWASP).</li>
 * </ul>
 */
public final class RelatorioCsv {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final String SEPARADOR = ";";
    static final String FIM_DE_LINHA = "\r\n";
    static final String BOM = "\uFEFF";
    static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Primeiros caracteres que o Excel interpreta como início de fórmula. */
    private static final String INICIO_DE_FORMULA = "=+-@\t\r";
    static final List<String> CABECALHO = List.of(
            "Data", "Id", "Tipo", "Categoria", "Descrição", "Usuário destino", "Valor (BRL)", "Status", "Motivo da rejeição");

    private RelatorioCsv() {
    }

    public static byte[] gerar(List<Transacao> transacoes) {
        StringBuilder csv = new StringBuilder(BOM);
        linha(csv, CABECALHO);
        for (Transacao t : transacoes) {
            linha(csv, List.of(
                    DATA_HORA.format(t.getCriadaEm().atZone(FUSO)),
                    texto(t.getId()),
                    t.getTipo().name(),
                    texto(t.getCategoria()),
                    texto(t.getDescricao()),
                    texto(t.getUsuarioDestinoId()),
                    valor(t.getValor()),
                    t.getStatus().name(),
                    t.getMotivoRejeicao() == null ? "" : t.getMotivoRejeicao().getDescricao()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void linha(StringBuilder csv, List<String> campos) {
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                csv.append(SEPARADOR);
            }
            csv.append(campo(semFormula(campos.get(i))));
        }
        csv.append(FIM_DE_LINHA);
    }

    /** Aspas só quando preciso (RFC 4180): separador, aspas ou quebra de linha; aspas internas dobram. */
    static String campo(String valor) {
        if (valor.contains(SEPARADOR) || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    /** Vírgula decimal, sem milhar: o Excel em pt-BR lê como número. O valor nunca é negativo. */
    static String valor(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }

    /** Neutraliza fórmula: a descrição é texto livre digitado pelo usuário. */
    static String semFormula(String valor) {
        if (!valor.isEmpty() && INICIO_DE_FORMULA.indexOf(valor.charAt(0)) >= 0) {
            return "'" + valor;
        }
        return valor;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
