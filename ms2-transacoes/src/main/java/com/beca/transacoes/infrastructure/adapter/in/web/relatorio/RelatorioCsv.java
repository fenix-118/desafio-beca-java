package com.beca.transacoes.infrastructure.adapter.in.web.relatorio;

import com.beca.transacoes.domain.model.Transacao;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

/** Escreve o relatório de transações em CSV (pedido 7). Fica no adaptador web: é formato de saída. */
public final class RelatorioCsv {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final String SEPARADOR = ";";
    static final String FIM_DE_LINHA = "\r\n";
    static final List<String> CABECALHO = List.of(
            "Data", "Id", "Tipo", "Categoria", "Descrição", "Usuário destino", "Valor (BRL)", "Status", "Motivo da rejeição");

    private RelatorioCsv() {
    }

    public static byte[] gerar(List<Transacao> transacoes) {
        StringBuilder csv = new StringBuilder();
        linha(csv, CABECALHO);
        for (Transacao t : transacoes) {
            linha(csv, List.of(
                    t.getCriadaEm().atZone(FUSO).toLocalDateTime().toString(),
                    texto(t.getId()),
                    t.getTipo().name(),
                    texto(t.getCategoria()),
                    texto(t.getDescricao()),
                    texto(t.getUsuarioDestinoId()),
                    t.getValor().toPlainString(),
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
            csv.append(campo(campos.get(i)));
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

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
