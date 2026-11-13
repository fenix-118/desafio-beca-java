package com.beca.transacoes.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Análise de despesas de um período (pedido 5). Só entram saídas APROVADAS (retirada, transferência
 * enviada e compra): depósitos e rejeitadas ficam de fora (tarefa 5.1, regra em {@link Transacao#isDespesa()}).
 *
 * @param porCategoria uma linha por categoria com despesa, da maior para a menor (tarefa 5.2)
 * @param porDia       uma linha por dia com despesa, em ordem cronológica (tarefa 5.3)
 * @param porMes       uma linha por mês com despesa, em ordem cronológica (tarefa 5.3)
 * @param totalGeral   soma de todas as despesas do período
 * @param quantidade   número de transações que entraram no resumo
 */
public record ResumoDespesas(List<TotalPorCategoria> porCategoria, List<TotalPorDia> porDia,
                             List<TotalPorMes> porMes, BigDecimal totalGeral, int quantidade) {

    public ResumoDespesas {
        porCategoria = List.copyOf(porCategoria);
        porDia = List.copyOf(porDia);
        porMes = List.copyOf(porMes);
    }

    /**
     * Monta o resumo a partir das transações do período, ignorando o que não é despesa.
     *
     * @param fuso fuso em que se conta o "dia" e o "mês" de cada transação (São Paulo, dúvida 11)
     */
    public static ResumoDespesas calcular(Collection<Transacao> transacoes, ZoneId fuso) {
        List<Transacao> despesas = transacoes.stream().filter(Transacao::isDespesa).toList();

        Map<Categoria, Acumulado> porCategoria = agrupar(despesas, Transacao::getCategoria, new EnumMap<>(Categoria.class));
        Map<LocalDate, Acumulado> porDia = agrupar(despesas, t -> diaDe(t, fuso), new TreeMap<>());
        Map<YearMonth, Acumulado> porMes = agrupar(despesas, t -> YearMonth.from(diaDe(t, fuso)), new TreeMap<>());

        BigDecimal totalGeral = despesas.stream().map(Transacao::getValor)
                .reduce(zero(), BigDecimal::add);

        List<TotalPorCategoria> linhasCategoria = porCategoria.entrySet().stream()
                .map(e -> new TotalPorCategoria(e.getKey(), e.getValue().total, e.getValue().quantidade))
                .sorted(Comparator.comparing(TotalPorCategoria::total).reversed()
                        .thenComparing(TotalPorCategoria::categoria))
                .toList();
        List<TotalPorDia> linhasDia = porDia.entrySet().stream()
                .map(e -> new TotalPorDia(e.getKey(), e.getValue().total, e.getValue().quantidade))
                .toList();
        List<TotalPorMes> linhasMes = porMes.entrySet().stream()
                .map(e -> new TotalPorMes(e.getKey(), e.getValue().total, e.getValue().quantidade))
                .toList();

        return new ResumoDespesas(linhasCategoria, linhasDia, linhasMes, totalGeral, despesas.size());
    }

    private static <K> Map<K, Acumulado> agrupar(List<Transacao> despesas, Function<Transacao, K> chave,
                                                 Map<K, Acumulado> destino) {
        for (Transacao t : despesas) {
            destino.computeIfAbsent(chave.apply(t), k -> new Acumulado()).somar(t.getValor());
        }
        return destino;
    }

    private static LocalDate diaDe(Transacao t, ZoneId fuso) {
        return LocalDate.ofInstant(t.getCriadaEm(), fuso);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(Transacao.CASAS_DECIMAIS);
    }

    public record TotalPorCategoria(Categoria categoria, BigDecimal total, int quantidade) {
    }

    public record TotalPorDia(LocalDate dia, BigDecimal total, int quantidade) {
    }

    public record TotalPorMes(YearMonth mes, BigDecimal total, int quantidade) {
    }

    private static final class Acumulado {
        private BigDecimal total = zero();
        private int quantidade;

        void somar(BigDecimal valor) {
            total = total.add(valor);
            quantidade++;
        }
    }
}
