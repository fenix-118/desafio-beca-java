package com.beca.transacoes.infrastructure.adapter.in.web.dto;

import com.beca.transacoes.domain.model.MoedaEstrangeira;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** O valor da transação numa moeda estrangeira, com a taxa e a data da cotação usada. */
public record ConversaoResponse(
        @Schema(example = "USD") MoedaEstrangeira moeda,
        @Schema(description = "false quando a BrasilAPI não respondeu no registro") boolean disponivel,
        @Schema(example = "21.76", nullable = true) BigDecimal valor,
        @Schema(description = "Reais por 1 unidade da moeda (PTAX de venda)", example = "5.5146", nullable = true)
        BigDecimal taxa,
        @Schema(description = "Data da cotação (num fim de semana, a do último dia útil)", example = "2026-11-09",
                nullable = true)
        LocalDate dataCotacao) {

    public static ConversaoResponse indisponivel(MoedaEstrangeira moeda) {
        return new ConversaoResponse(moeda, false, null, null, null);
    }
}
