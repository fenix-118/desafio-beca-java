package com.beca.usuarios.infrastructure.adapter.in.web;

import com.beca.usuarios.application.port.in.AtualizarUsuarioUseCase;
import com.beca.usuarios.application.port.in.AtualizarUsuarioUseCase.AtualizarUsuarioCommand;
import com.beca.usuarios.application.port.in.BuscarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase.CriarUsuarioCommand;
import com.beca.usuarios.application.port.in.ExcluirUsuarioUseCase;
import com.beca.usuarios.domain.model.Usuario;
import com.beca.usuarios.infrastructure.adapter.in.web.dto.AtualizarUsuarioRequest;
import com.beca.usuarios.infrastructure.adapter.in.web.dto.CriarUsuarioRequest;
import com.beca.usuarios.infrastructure.adapter.in.web.dto.UsuarioResponse;
import com.beca.usuarios.infrastructure.adapter.in.web.mapper.UsuarioWebMapper;
import com.beca.usuarios.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
import com.beca.usuarios.infrastructure.adapter.in.web.seguranca.Dono;
import com.beca.usuarios.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Usuários", description = "Cadastro aberto; consulta, alteração e exclusão só da própria conta (login básico)")
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final CriarUsuarioUseCase criarUsuario;
    private final BuscarUsuarioUseCase buscarUsuario;
    private final AtualizarUsuarioUseCase atualizarUsuario;
    private final ExcluirUsuarioUseCase excluirUsuario;

    public UsuarioController(CriarUsuarioUseCase criarUsuario,
                             BuscarUsuarioUseCase buscarUsuario,
                             AtualizarUsuarioUseCase atualizarUsuario,
                             ExcluirUsuarioUseCase excluirUsuario) {
        this.criarUsuario = criarUsuario;
        this.buscarUsuario = buscarUsuario;
        this.atualizarUsuario = atualizarUsuario;
        this.excluirUsuario = excluirUsuario;
    }

    @Operation(summary = "Cria um usuário",
            description = "Retorna 201 com o usuário criado, 400 se os dados forem inválidos e 409 se o e-mail já existir.")
    @ApiResponse(responseCode = "201", description = "Usuário criado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody CriarUsuarioRequest request) {
        Usuario usuario = criarUsuario.criar(
                new CriarUsuarioCommand(request.nome(), request.email(), request.senha()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(usuario.getId())
                .toUri();
        return ResponseEntity.created(location).body(UsuarioWebMapper.paraResposta(usuario));
    }

    @Operation(summary = "Busca um usuário pelo id",
            description = "Retorna 404 se não existir.")
    @ApiResponse(responseCode = "200", description = "Usuário encontrado")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem login, ou e-mail e senha inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "A conta é de outro usuário",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @SecurityRequirement(name = OpenApiConfig.LOGIN_BASICO)
    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(
            @PathVariable Long id,
            @Parameter(hidden = true)
            @RequestAttribute(name = AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, required = false) Long usuarioAutenticado,
            @Parameter(hidden = true)
            @RequestAttribute(name = AutenticacaoBasicaFilter.ATRIBUTO_SERVICO, required = false) Boolean chamadaDeServico) {
        // O MS2 consulta qualquer usuário (dono e destino das transações) com o token de serviço.
        if (!Boolean.TRUE.equals(chamadaDeServico)) {
            Dono.exigir(usuarioAutenticado, id);
        }
        return UsuarioWebMapper.paraResposta(buscarUsuario.buscarPorId(id));
    }

    @Operation(summary = "Atualiza um usuário",
            description = "Retorna 200 com o usuário atualizado, 400 se os dados forem inválidos, 404 se não existir e 409 se o novo e-mail já estiver em uso. A senha é opcional: se vier vazia, a atual é mantida.")
    @ApiResponse(responseCode = "200", description = "Usuário atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado para outro usuário",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem login, ou e-mail e senha inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "A conta é de outro usuário",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @SecurityRequirement(name = OpenApiConfig.LOGIN_BASICO)
    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id,
                                     @Valid @RequestBody AtualizarUsuarioRequest request,
                                     @Parameter(hidden = true)
                                     @RequestAttribute(name = AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, required = false) Long usuarioAutenticado) {
        Dono.exigir(usuarioAutenticado, id);
        Usuario usuario = atualizarUsuario.atualizar(
                id, new AtualizarUsuarioCommand(request.nome(), request.email(), request.senha()));
        return UsuarioWebMapper.paraResposta(usuario);
    }

    @Operation(summary = "Exclui um usuário",
            description = "Retorna 204 se excluído e 404 se não existir.")
    @ApiResponse(responseCode = "204", description = "Usuário excluído", content = @Content)
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem login, ou e-mail e senha inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "A conta é de outro usuário",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @SecurityRequirement(name = OpenApiConfig.LOGIN_BASICO)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id,
                                        @Parameter(hidden = true)
                                        @RequestAttribute(name = AutenticacaoBasicaFilter.ATRIBUTO_USUARIO, required = false) Long usuarioAutenticado) {
        Dono.exigir(usuarioAutenticado, id);
        excluirUsuario.excluir(id);
        return ResponseEntity.noContent().build();
    }
}