package com.beca.transacoes.infrastructure.adapter.in.web.dto;

import com.beca.transacoes.domain.model.Categoria;
import com.beca.transacoes.domain.model.MensagensTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** As mensagens vêm de MensagensTransacao: a mesma regra responde igual aqui e no domínio. */
public record RegistrarTransacaoRequest(
        @Schema(example = "1")
        @NotNull(message = MensagensTransacao.USUARIO_OBRIGATORIO)
        @Positive(message = MensagensTransacao.USUARIO_OBRIGATORIO)
        Long usuarioId,

        @Schema(example = "DEPOSITO")
        @NotNull(message = MensagensTransacao.TIPO_OBRIGATORIO)
        TipoTransacao tipo,

        @Schema(example = "250.00")
        @NotNull(message = MensagensTransacao.VALOR_OBRIGATORIO)
        @DecimalMin(value = "0.01", message = MensagensTransacao.VALOR_POSITIVO)
        @Digits(integer = 13, fraction = 2, message = MensagensTransacao.VALOR_FORMATO)
        BigDecimal valor,

        @Schema(description = "Obrigatória em RETIRADA, TRANSFERENCIA e COMPRA; não se aplica a DEPOSITO",
                example = "ALIMENTACAO", nullable = true)
        Categoria categoria,

        @Schema(description = "Obrigatório só em TRANSFERENCIA", nullable = true)
        @Positive(message = MensagensTransacao.DESTINO_INVALIDO)
        Long usuarioDestinoId,

        @Schema(example = "Salário de novembro", nullable = true)
        @Size(max = 140, message = MensagensTransacao.DESCRICAO_TAMANHO)
        String descricao) {
}
