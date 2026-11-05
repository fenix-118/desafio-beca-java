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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class UsuarioService implements
        CriarUsuarioUseCase,
        BuscarUsuarioUseCase,
        AtualizarUsuarioUseCase,
        ExcluirUsuarioUseCase {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int NOME_MAX = 120;
    private static final int EMAIL_MAX = 160;
    private static final int SENHA_MIN = 8;
    /** Limite do BCrypt: ele considera no máximo 72 bytes da senha. */
    private static final int SENHA_MAX_BYTES = 72;

    private final UsuarioRepositoryPort repositorio;
    private final SenhaEncoderPort senhaEncoder;

    public UsuarioService(UsuarioRepositoryPort repositorio, SenhaEncoderPort senhaEncoder) {
        this.repositorio = repositorio;
        this.senhaEncoder = senhaEncoder;
    }

    @Override
    public Usuario criar(CriarUsuarioCommand comando) {
        String nome = validarNome(comando.nome());
        String email = normalizarEmail(comando.email());
        validarSenha(comando.senha());
        if (repositorio.existePorEmail(email)) {
            throw new EmailJaCadastradoException();
        }
        Usuario usuario = Usuario.novo(nome, email, senhaEncoder.codificar(comando.senha()));
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
        String nome = validarNome(comando.nome());
        String email = normalizarEmail(comando.email());
        boolean trocarSenha = comando.senha() != null && !comando.senha().isBlank();
        if (trocarSenha) {
            validarSenha(comando.senha());
        }
        if (!email.equals(usuario.getEmail()) && repositorio.existePorEmail(email)) {
            throw new EmailJaCadastradoException();
        }
        usuario.atualizarDados(nome, email);
        if (trocarSenha) {
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

    private String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new DadosInvalidosException("O campo 'nome' é obrigatório.");
        }
        String limpo = nome.trim();
        if (limpo.length() > NOME_MAX) {
            throw new DadosInvalidosException("O campo 'nome' deve ter no máximo " + NOME_MAX + " caracteres.");
        }
        return limpo;
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DadosInvalidosException("O campo 'email' é obrigatório.");
        }
        String limpo = email.trim().toLowerCase(Locale.ROOT);
        if (limpo.length() > EMAIL_MAX || !EMAIL.matcher(limpo).matches()) {
            throw new DadosInvalidosException("E-mail inválido.");
        }
        return limpo;
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new DadosInvalidosException("O campo 'senha' é obrigatório.");
        }
        if (senha.length() < SENHA_MIN) {
            throw new DadosInvalidosException(
                    "A senha deve ter no mínimo " + SENHA_MIN + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAX_BYTES) {
            throw new DadosInvalidosException(
                    "A senha é longa demais: o limite é de " + SENHA_MAX_BYTES
                            + " bytes, e letras acentuadas ou símbolos ocupam mais de um byte cada.");
        }
    }
}