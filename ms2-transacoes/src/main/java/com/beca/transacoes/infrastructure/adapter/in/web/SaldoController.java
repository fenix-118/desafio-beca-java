package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.ConsultarSaldoUseCase;
import com.beca.transacoes.application.port.in.ConsultarSaldoUseCase.SaldoDaConta;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.SaldoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Saldo", description = "Saldo e limites da conta do usuário, lidos da API Mock")
@RestController
@RequestMapping("/contas")
public class SaldoController {

    /** Moeda base de todas as contas (dúvida 10 do refinamento). */
    static final String MOEDA_BASE = "BRL";

    private final ConsultarSaldoUseCase consultarSaldo;

    public SaldoController(ConsultarSaldoUseCase consultarSaldo) {
        this.consultarSaldo = consultarSaldo;
    }

    @Operation(summary = "Consulta o saldo do usuário",
            description = "Lê o saldo e os limites na API Mock no momento da chamada (sem cache) e soma as saídas "
                    + "aprovadas hoje. Responde 404 se o usuário não existir no MS1 ou não tiver conta, e 503 se a "
                    + "API Mock ou o MS1 estiverem fora do ar.")
    @ApiResponse(responseCode = "200", description = "Saldo e limites atuais")
    @ApiResponse(responseCode = "404", description = "Usuário inexistente ou sem conta",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "API Mock ou MS1 indisponível",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{usuarioId}/saldo")
    public SaldoResponse consultar(@Parameter(description = "Id do usuário no MS1", example = "3")
                                   @PathVariable Long usuarioId) {
        SaldoDaConta s = consultarSaldo.consultar(usuarioId);
        return new SaldoResponse(s.usuarioId(), s.saldo(), MOEDA_BASE, s.limitePorTransacao(), s.limiteDiario(),
                s.gastoHoje(), s.limiteDiarioDisponivel(), s.consultadoEm());
    }
}
