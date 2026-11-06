package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.BuscarTransacaoUseCase;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase;
import com.beca.transacoes.domain.model.Transacao;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.RegistrarTransacaoRequest;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.TransacaoResponse;
import com.beca.transacoes.infrastructure.adapter.in.web.mapper.TransacaoWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Transações", description = "Registro e consulta de transações financeiras")
@RestController
@RequestMapping("/transacoes")
public class TransacaoController {

    private final RegistrarTransacaoUseCase registrarTransacao;
    private final BuscarTransacaoUseCase buscarTransacao;

    public TransacaoController(RegistrarTransacaoUseCase registrarTransacao,
                               BuscarTransacaoUseCase buscarTransacao) {
        this.registrarTransacao = registrarTransacao;
        this.buscarTransacao = buscarTransacao;
    }

    @Operation(summary = "Registra uma transação",
            description = "Avalia e grava a transação. Responde 201 com o id e o status (APROVADA ou REJEITADA); "
                    + "uma transação rejeitada também é gravada e traz o motivo. Responde 400 se os dados forem inválidos.")
    @ApiResponse(responseCode = "201", description = "Transação registrada (aprovada ou rejeitada)")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<TransacaoResponse> registrar(@Valid @RequestBody RegistrarTransacaoRequest request) {
        Transacao transacao = registrarTransacao.registrar(TransacaoWebMapper.paraComando(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transacao.getId())
                .toUri();
        return ResponseEntity.created(location).body(TransacaoWebMapper.paraResposta(transacao));
    }

    @Operation(summary = "Busca uma transação pelo id", description = "Retorna 404 se não existir.")
    @ApiResponse(responseCode = "200", description = "Transação encontrada")
    @ApiResponse(responseCode = "404", description = "Transação não encontrada",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public TransacaoResponse buscarPorId(@PathVariable Long id) {
        return TransacaoWebMapper.paraResposta(buscarTransacao.buscarPorId(id));
    }
}
