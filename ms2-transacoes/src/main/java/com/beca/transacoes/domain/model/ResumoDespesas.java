package com.beca.transacoes.domain.model;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Análise de despesas de um período (pedido 5). Só entram saídas APROVADAS (retirada, transferência
 * enviada e compra): depósitos e rejeitadas ficam de fora (tarefa 5.1, regra em {@link Transacao#isDespesa()}).
 *
 * @param porCategoria uma linha por categoria com despesa, da maior para a menor
 * @param totalGeral   soma de todas as despesas do período
 * @param quantidade   número de transações que entraram no resumo
 */
public record ResumoDespesas(List<TotalPorCategoria> porCategoria, BigDecimal totalGeral, int quantidade) {

    public ResumoDespesas {
        porCategoria = List.copyOf(porCategoria);
    }

    /** Monta o resumo a partir das transações do período, ignorando o que não é despesa. */
    public static ResumoDespesas calcular(Collection<Transacao> transacoes) {
        List<Transacao> despesas = transacoes.stream().filter(Transacao::isDespesa).toList();

        Map<Categoria, Acumulado> porCategoria = new EnumMap<>(Categoria.class);
        BigDecimal totalGeral = BigDecimal.ZERO.setScale(Transacao.CASAS_DECIMAIS);
        for (Transacao t : despesas) {
            porCategoria.computeIfAbsent(t.getCategoria(), c -> new Acumulado()).somar(t.getValor());
            totalGeral = totalGeral.add(t.getValor());
        }

        List<TotalPorCategoria> linhas = porCategoria.entrySet().stream()
                .map(e -> new TotalPorCategoria(e.getKey(), e.getValue().total, e.getValue().quantidade))
                .sorted(Comparator.comparing(TotalPorCategoria::total).reversed()
                        .thenComparing(TotalPorCategoria::categoria))
                .toList();

        return new ResumoDespesas(linhas, totalGeral, despesas.size());
    }

    public record TotalPorCategoria(Categoria categoria, BigDecimal total, int quantidade) {
    }

    private static final class Acumulado {
        private BigDecimal total = BigDecimal.ZERO.setScale(Transacao.CASAS_DECIMAIS);
        private int quantidade;

        void somar(BigDecimal valor) {
            total = total.add(valor);
            quantidade++;
        }
    }
}
