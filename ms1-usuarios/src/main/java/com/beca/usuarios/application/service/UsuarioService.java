package com.beca.usuarios.application.service;

import com.beca.usuarios.application.port.in.AtualizarUsuarioUseCase;
import com.beca.usuarios.application.port.in.AutenticarUsuarioUseCase;
import com.beca.usuarios.application.port.in.BuscarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase;
import com.beca.usuarios.application.port.in.ExcluirUsuarioUseCase;
import com.beca.usuarios.application.port.out.SenhaEncoderPort;
import com.beca.usuarios.application.port.out.UsuarioRepositoryPort;
import com.beca.usuarios.domain.exception.CredenciaisInvalidasException;
import com.beca.usuarios.domain.exception.DadosInvalidosException;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import com.beca.usuarios.domain.exception.UsuarioNaoEncontradoException;
import com.beca.usuarios.domain.model.MensagensUsuario;
import com.beca.usuarios.domain.model.Usuario;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class UsuarioService implements
        CriarUsuarioUseCase,
        BuscarUsuarioUseCase,
        AtualizarUsuarioUseCase,
        ExcluirUsuarioUseCase,
        AutenticarUsuarioUseCase {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int NOME_MAX = MensagensUsuario.NOME_MAX;
    private static final int EMAIL_MAX = MensagensUsuario.EMAIL_MAX;
    private static final int SENHA_MIN = MensagensUsuario.SENHA_MIN;
    private static final int SENHA_MAX_BYTES = MensagensUsuario.SENHA_MAX_BYTES;

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

    /**
     * E-mail inexistente e senha errada respondem igual (CredenciaisInvalidasException), e a senha
     * nunca vai para log nem para exceção. Senha vazia ou acima de 72 bytes nem chega ao BCrypt.
     */
    @Override
    public Usuario autenticar(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isEmpty()
                || senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAX_BYTES) {
            throw new CredenciaisInvalidasException();
        }
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        return repositorio.buscarPorEmail(emailNormalizado)
                .filter(u -> senhaEncoder.confere(senha, u.getSenhaHash()))
                .orElseThrow(CredenciaisInvalidasException::new);
    }

    private String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new DadosInvalidosException(MensagensUsuario.NOME_OBRIGATORIO);
        }
        String limpo = nome.trim();
        if (limpo.length() > NOME_MAX) {
            throw new DadosInvalidosException(MensagensUsuario.NOME_TAMANHO);
        }
        return limpo;
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DadosInvalidosException(MensagensUsuario.EMAIL_OBRIGATORIO);
        }
        String limpo = email.trim().toLowerCase(Locale.ROOT);
        if (limpo.length() > EMAIL_MAX) {
            throw new DadosInvalidosException(MensagensUsuario.EMAIL_TAMANHO);
        }
        if (!EMAIL.matcher(limpo).matches()) {
            throw new DadosInvalidosException(MensagensUsuario.EMAIL_INVALIDO);
        }
        return limpo;
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new DadosInvalidosException(MensagensUsuario.SENHA_OBRIGATORIA);
        }
        if (senha.length() < SENHA_MIN) {
            throw new DadosInvalidosException(MensagensUsuario.SENHA_CURTA);
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAX_BYTES) {
            throw new DadosInvalidosException(MensagensUsuario.SENHA_LONGA);
        }
    }
}