package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase;
import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase.ConsultarDespesasQuery;
import com.beca.transacoes.application.port.in.ConsultarDespesasUseCase.ResumoDoPeriodo;
import com.beca.transacoes.domain.model.ResumoDespesas;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.ResumoDespesasResponse;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.ResumoDespesasResponse.CategoriaResponse;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.ResumoDespesasResponse.DiaResponse;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.ResumoDespesasResponse.MesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Despesas", description = "Análise das despesas (saídas aprovadas) do usuário")
@RestController
@RequestMapping("/despesas")
public class DespesaController {

    /** Moeda base de todas as contas (dúvida 10 do refinamento). */
    static final String MOEDA_BASE = "BRL";

    private final ConsultarDespesasUseCase consultarDespesas;

    public DespesaController(ConsultarDespesasUseCase consultarDespesas) {
        this.consultarDespesas = consultarDespesas;
    }

    @Operation(summary = "Resumo das despesas do usuário no período",
            description = "Soma só as saídas APROVADAS (retirada, transferência enviada e compra); depósitos e "
                    + "transações rejeitadas não entram. Traz o total por categoria, por dia e por mês. As datas são dias no fuso de São Paulo e o dia final é "
                    + "incluído. Sem 'inicio' e 'fim', usa o mês atual. Responde 400 se só uma das datas for informada "
                    + "ou se 'inicio' for depois de 'fim'.")
    @ApiResponse(responseCode = "200", description = "Resumo por categoria, por dia e por mês, com o total geral")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/resumo")
    public ResumoDespesasResponse resumo(
            @Parameter(description = "Id do usuário", example = "3")
            @RequestParam Long usuarioId,
            @Parameter(description = "Primeiro dia (aaaa-mm-dd); sem ele e sem 'fim', o mês atual", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Último dia, incluído (aaaa-mm-dd)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        ResumoDoPeriodo r = consultarDespesas.consultar(new ConsultarDespesasQuery(usuarioId, inicio, fim));
        ResumoDespesas resumo = r.resumo();
        return new ResumoDespesasResponse(r.usuarioId(), r.inicio(), r.fim(), MOEDA_BASE,
                resumo.totalGeral(), resumo.quantidade(),
                resumo.porCategoria().stream()
                        .map(c -> new CategoriaResponse(c.categoria(), c.total(), c.quantidade()))
                        .toList(),
                resumo.porDia().stream()
                        .map(d -> new DiaResponse(d.dia(), d.total(), d.quantidade()))
                        .toList(),
                resumo.porMes().stream()
                        .map(mes -> new MesResponse(mes.mes().toString(), mes.total(), mes.quantidade()))
                        .toList());
    }
}
