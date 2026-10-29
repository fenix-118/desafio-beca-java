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
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

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

    @GetMapping
    public List<UsuarioResponse> listar() {
        return buscarUsuario.listarTodos().stream()
                .map(UsuarioWebMapper::paraResposta)
                .toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable Long id) {
        return UsuarioWebMapper.paraResposta(buscarUsuario.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id,
                                     @Valid @RequestBody AtualizarUsuarioRequest request) {
        Usuario usuario = atualizarUsuario.atualizar(
                id, new AtualizarUsuarioCommand(request.nome(), request.email(), request.senha()));
        return UsuarioWebMapper.paraResposta(usuario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        excluirUsuario.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
