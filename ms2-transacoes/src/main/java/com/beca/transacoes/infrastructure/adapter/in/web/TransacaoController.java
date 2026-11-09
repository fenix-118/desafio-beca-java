package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.in.BuscarTransacaoUseCase;
import com.beca.transacoes.application.port.in.ListarTransacoesUseCase;
import com.beca.transacoes.application.port.in.ListarTransacoesUseCase.ListarTransacoesQuery;
import com.beca.transacoes.application.port.in.RegistrarTransacaoUseCase;
import com.beca.transacoes.domain.model.TipoTransacao;
import com.beca.transacoes.domain.model.Transacao;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.RegistrarTransacaoRequest;
import com.beca.transacoes.infrastructure.adapter.in.web.dto.TransacaoResponse;
import com.beca.transacoes.infrastructure.adapter.in.web.mapper.TransacaoWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Transações", description = "Registro e consulta de transações financeiras")
@RestController
@RequestMapping("/transacoes")
public class TransacaoController {

    private final RegistrarTransacaoUseCase registrarTransacao;
    private final BuscarTransacaoUseCase buscarTransacao;
    private final ListarTransacoesUseCase listarTransacoes;

    public TransacaoController(RegistrarTransacaoUseCase registrarTransacao,
                               BuscarTransacaoUseCase buscarTransacao,
                               ListarTransacoesUseCase listarTransacoes) {
        this.registrarTransacao = registrarTransacao;
        this.buscarTransacao = buscarTransacao;
        this.listarTransacoes = listarTransacoes;
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

    @Operation(summary = "Lista as transações de um usuário",
            description = "Da mais recente para a mais antiga. Período e tipo são opcionais; as datas são dias "
                    + "no fuso de São Paulo e o dia final é incluído. Responde 400 se 'inicio' for depois de 'fim'.")
    @ApiResponse(responseCode = "200", description = "Lista de transações (pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public List<TransacaoResponse> listar(
            @Parameter(description = "Id do usuário", example = "1")
            @RequestParam Long usuarioId,
            @Parameter(description = "Primeiro dia (aaaa-mm-dd)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Último dia, incluído (aaaa-mm-dd)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @Parameter(description = "Filtra por tipo")
            @RequestParam(required = false) TipoTransacao tipo) {
        return listarTransacoes.listar(new ListarTransacoesQuery(usuarioId, inicio, fim, tipo)).stream()
                .map(TransacaoWebMapper::paraResposta)
                .toList();
    }
}
