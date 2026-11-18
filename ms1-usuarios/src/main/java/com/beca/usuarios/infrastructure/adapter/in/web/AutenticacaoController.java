package com.beca.usuarios.infrastructure.adapter.in.web;

import com.beca.usuarios.application.port.in.AutenticarUsuarioUseCase;
import com.beca.usuarios.domain.model.Usuario;
import com.beca.usuarios.infrastructure.adapter.in.web.dto.AutenticacaoRequest;
import com.beca.usuarios.infrastructure.adapter.in.web.dto.UsuarioAutenticadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Validação de credenciais, usada pelo MS2 a cada requisição")
@RestController
@RequestMapping("/autenticacao")
public class AutenticacaoController {

    private final AutenticarUsuarioUseCase autenticarUsuario;

    public AutenticacaoController(AutenticarUsuarioUseCase autenticarUsuario) {
        this.autenticarUsuario = autenticarUsuario;
    }

    @Operation(summary = "Valida e-mail e senha",
            description = "Retorna o id, o nome e o e-mail do usuário se a senha conferir. E-mail inexistente e "
                    + "senha errada respondem o mesmo 401, para não revelar quais e-mails estão cadastrados.")
    @ApiResponse(responseCode = "200", description = "Credenciais válidas")
    @ApiResponse(responseCode = "400", description = "E-mail ou senha não informados",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public UsuarioAutenticadoResponse autenticar(@Valid @RequestBody AutenticacaoRequest request) {
        Usuario usuario = autenticarUsuario.autenticar(request.email(), request.senha());
        return new UsuarioAutenticadoResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
