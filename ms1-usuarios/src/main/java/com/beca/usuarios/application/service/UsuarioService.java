package com.beca.usuarios.application.service;

import com.beca.usuarios.application.port.in.AtualizarUsuarioUseCase;
import com.beca.usuarios.application.port.in.BuscarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase;
import com.beca.usuarios.application.port.in.ExcluirUsuarioUseCase;
import com.beca.usuarios.application.port.out.SenhaEncoderPort;
import com.beca.usuarios.application.port.out.UsuarioRepositoryPort;
import com.beca.usuarios.domain.exception.DadosInvalidosException;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import com.beca.usuarios.domain.exception.UsuarioNaoEncontradoException;
import com.beca.usuarios.domain.model.Usuario;

import java.util.List;
import java.util.Locale;

public class UsuarioService implements
        CriarUsuarioUseCase,
        BuscarUsuarioUseCase,
        AtualizarUsuarioUseCase,
        ExcluirUsuarioUseCase {

    private final UsuarioRepositoryPort repositorio;
    private final SenhaEncoderPort senhaEncoder;

    public UsuarioService(UsuarioRepositoryPort repositorio, SenhaEncoderPort senhaEncoder) {
        this.repositorio = repositorio;
        this.senhaEncoder = senhaEncoder;
    }

    @Override
    public Usuario criar(CriarUsuarioCommand comando) {
        String email = normalizarEmail(comando.email());
        if (comando.senha() == null || comando.senha().isBlank()) {
            throw new DadosInvalidosException("O campo 'senha' é obrigatório.");
        }
        if (repositorio.existePorEmail(email)) {
            throw new EmailJaCadastradoException();
        }
        Usuario usuario = Usuario.novo(comando.nome(), email, senhaEncoder.codificar(comando.senha()));
        return repositorio.salvar(usuario);
    }

    @Override
    public Usuario buscarPorId(Long id) {
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));
    }

    @Override
    public List<Usuario> listarTodos() {
        return repositorio.listarTodos();
    }

    @Override
    public Usuario atualizar(Long id, AtualizarUsuarioCommand comando) {
        Usuario usuario = buscarPorId(id);
        String email = normalizarEmail(comando.email());
        if (!email.equals(usuario.getEmail()) && repositorio.existePorEmail(email)) {
            throw new EmailJaCadastradoException();
        }
        usuario.atualizarDados(comando.nome(), email);
        if (comando.senha() != null && !comando.senha().isBlank()) {
            usuario.alterarSenhaHash(senhaEncoder.codificar(comando.senha()));
        }
        return repositorio.salvar(usuario);
    }

    @Override
    public void excluir(Long id) {
        if (!repositorio.existePorId(id)) {
            throw new UsuarioNaoEncontradoException(id);
        }
        repositorio.excluirPorId(id);
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DadosInvalidosException("O campo 'email' é obrigatório.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
