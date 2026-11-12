package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.GerarRelatorioUseCase;
import com.beca.transacoes.application.port.in.GerarRelatorioUseCase.GerarRelatorioQuery;
import com.beca.transacoes.application.port.in.GerarRelatorioUseCase.RelatorioDoPeriodo;
import com.beca.transacoes.infrastructure.adapter.in.web.relatorio.RelatorioCsv;
import com.beca.transacoes.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
import com.beca.transacoes.infrastructure.adapter.in.web.seguranca.Dono;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "Relatórios", description = "Relatório das transações do usuário logado")
@RestController
@RequestMapping("/transacoes")
public class RelatorioController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final GerarRelatorioUseCase gerarRelatorio;

    public RelatorioController(GerarRelatorioUseCase gerarRelatorio) {
        this.gerarRelatorio = gerarRelatorio;
    }

    @Operation(summary = "Relatório CSV das transações do usuário logado",
            description = "Baixa um CSV com todas as transações do usuário do login (aprovadas e rejeitadas), "
                    + "da mais antiga para a mais recente. O usuário vem do login: não há 'usuarioId'. As datas são "
                    + "dias no fuso de São Paulo e o dia final é incluído. Sem 'inicio' e 'fim', usa o mês atual. "
                    + "Responde 400 se só uma das datas for informada, se 'inicio' for depois de 'fim' ou se o "
                    + "período passar de 366 dias.")
    @ApiResponse(responseCode = "200", description = "Arquivo CSV (download)",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Período inválido",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem login ou login inválido",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/relatorio")
    public ResponseEntity<byte[]> relatorio(
            @Parameter(description = "Primeiro dia (aaaa-mm-dd); sem ele e sem 'fim', o mês atual", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Último dia, incluído (aaaa-mm-dd)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @Parameter(hidden = true)
            @RequestAttribute(name = AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, required = false) Long usuarioAutenticado) {
        Dono.exigir(usuarioAutenticado, null);
        RelatorioDoPeriodo r = gerarRelatorio.gerar(new GerarRelatorioQuery(usuarioAutenticado, inicio, fim));

        String arquivo = "transacoes-" + r.inicio() + "-a-" + r.fim() + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo).build().toString())
                .body(RelatorioCsv.gerar(r.transacoes()));
    }
}
