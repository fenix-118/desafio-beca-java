package com.beca.usuarios.application.service;

import com.beca.usuarios.application.port.in.AtualizarUsuarioUseCase.AtualizarUsuarioCommand;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase.CriarUsuarioCommand;
import com.beca.usuarios.application.port.out.SenhaEncoderPort;
import com.beca.usuarios.application.port.out.UsuarioRepositoryPort;
import com.beca.usuarios.domain.exception.CredenciaisInvalidasException;
import com.beca.usuarios.domain.exception.DadosInvalidosException;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import com.beca.usuarios.domain.exception.UsuarioNaoEncontradoException;
import com.beca.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepositoryPort repositorio;

    @Mock
    private SenhaEncoderPort senhaEncoder;

    @InjectMocks
    private UsuarioService service;

    // ---------- criar ----------

    @Test
    void deveCriarUsuarioComEmailNormalizadoESenhaCodificada() {
        when(repositorio.existePorEmail("ana@beca.com")).thenReturn(false);
        when(senhaEncoder.codificar("Senha@123")).thenReturn("hash-bcrypt");
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario criado = service.criar(new CriarUsuarioCommand("  Ana  ", "  Ana@Beca.COM ", "Senha@123"));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).salvar(salvo.capture());
        assertThat(salvo.getValue().getNome()).isEqualTo("Ana");
        assertThat(salvo.getValue().getEmail()).isEqualTo("ana@beca.com");
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("hash-bcrypt"); // ← getter do hash
        assertThat(criado).isSameAs(salvo.getValue());
    }

    @Test
    void naoDeveCriarUsuarioComEmailJaCadastrado() {
        when(repositorio.existePorEmail("ana@beca.com")).thenReturn(true);

        assertThrows(EmailJaCadastradoException.class,
                () -> service.criar(new CriarUsuarioCommand("Ana", "ana@beca.com", "Senha@123")));

        verify(repositorio, never()).salvar(any());
        verifyNoInteractions(senhaEncoder);
    }

    @Test
    void naoDeveCriarUsuarioSemNome() {
        assertThrows(DadosInvalidosException.class,
                () -> service.criar(new CriarUsuarioCommand("  ", "ana@beca.com", "Senha@123")));

        verifyNoInteractions(repositorio, senhaEncoder);
    }

    @Test
    void naoDeveCriarUsuarioComEmailInvalido() {
        assertThrows(DadosInvalidosException.class,
                () -> service.criar(new CriarUsuarioCommand("Ana", "ana.beca.com", "Senha@123")));

        verifyNoInteractions(repositorio, senhaEncoder);
    }

    @Test
    void naoDeveCriarUsuarioComSenhaCurta() {
        assertThrows(DadosInvalidosException.class,
                () -> service.criar(new CriarUsuarioCommand("Ana", "ana@beca.com", "123")));

        verifyNoInteractions(repositorio, senhaEncoder);
    }

    @Test
    void deveAceitarSenhaComExatamente72Bytes() {
        String senha = "a".repeat(72);
        when(repositorio.existePorEmail("ana@beca.com")).thenReturn(false);
        when(senhaEncoder.codificar(senha)).thenReturn("hash");
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.criar(new CriarUsuarioCommand("Ana", "ana@beca.com", senha));

        verify(senhaEncoder).codificar(senha);
    }

    @Test
    void naoDeveCriarUsuarioComSenhaAcimaDe72BytesMesmoComMenosDe72Caracteres() {
        String senha = "é".repeat(40); // 40 caracteres, mas 80 bytes em UTF-8
        assertThat(senha).hasSize(40);

        assertThrows(DadosInvalidosException.class,
                () -> service.criar(new CriarUsuarioCommand("Ana", "ana@beca.com", senha)));

        verifyNoInteractions(repositorio, senhaEncoder);
    }

    @Test
    void naoDeveAtualizarParaSenhaAcimaDe72Bytes() {
        Usuario existente = Usuario.novo("Ana", "ana@beca.com", "hash");
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(existente));

        assertThrows(DadosInvalidosException.class,
                () -> service.atualizar(1L, new AtualizarUsuarioCommand("Ana", "ana@beca.com", "é".repeat(40))));

        verify(repositorio, never()).salvar(any());
        verifyNoInteractions(senhaEncoder);
    }

    // ---------- buscar e listar ----------

    @Test
    void deveBuscarUsuarioPorId() {
        Usuario existente = Usuario.novo("Ana", "ana@beca.com", "hash");
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(existente));

        Usuario encontrado = service.buscarPorId(1L);

        assertThat(encontrado).isSameAs(existente);
    }

    @Test
    void deveLancarExcecaoAoBuscarUsuarioInexistente() {
        when(repositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNaoEncontradoException.class, () -> service.buscarPorId(99L));
    }

    @Test
    void deveListarTodosOsUsuarios() {
        List<Usuario> usuarios = List.of(
                Usuario.novo("Ana", "ana@beca.com", "hash1"),
                Usuario.novo("Bruno", "bruno@beca.com", "hash2"));
        when(repositorio.listarTodos()).thenReturn(usuarios);

        assertThat(service.listarTodos()).isEqualTo(usuarios);
    }

    // ---------- atualizar ----------

    @Test
    void deveAtualizarNomeEEmailSemTrocarSenha() {
        Usuario existente = Usuario.novo("Ana", "ana@beca.com", "hash-antigo");
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(existente));
        when(repositorio.existePorEmail("ana.silva@beca.com")).thenReturn(false);
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario atualizado = service.atualizar(1L,
                new AtualizarUsuarioCommand("Ana Silva", "ana.silva@beca.com", null));

        assertThat(atualizado.getNome()).isEqualTo("Ana Silva");
        assertThat(atualizado.getEmail()).isEqualTo("ana.silva@beca.com");
        assertThat(atualizado.getSenhaHash()).isEqualTo("hash-antigo"); // ← getter do hash
        verifyNoInteractions(senhaEncoder);
    }

    @Test
    void deveTrocarSenhaQuandoInformadaNaAtualizacao() {
        Usuario existente = Usuario.novo("Ana", "ana@beca.com", "hash-antigo");
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(existente));
        when(senhaEncoder.codificar("NovaSenha@123")).thenReturn("hash-novo");
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario atualizado = service.atualizar(1L,
                new AtualizarUsuarioCommand("Ana", "ana@beca.com", "NovaSenha@123"));

        assertThat(atualizado.getSenhaHash()).isEqualTo("hash-novo"); // ← getter do hash
    }

    @Test
    void devePermitirManterOMesmoEmailNaAtualizacao() {
        Usuario existente = Usuario.novo("Ana", "ana@beca.com", "hash");
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(existente));
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.atualizar(1L, new AtualizarUsuarioCommand("Ana Silva", "ana@beca.com", null));

        verify(repositorio, never()).existePorEmail(anyString());
        verify(repositorio).salvar(existente);
    }

    @Test
    void naoDeveAtualizarParaEmailDeOutroUsuario() {
        Usuario existente = Usuario.novo("Ana", "ana@beca.com", "hash");
        when(repositorio.buscarPorId(1L)).thenReturn(Optional.of(existente));
        when(repositorio.existePorEmail("bruno@beca.com")).thenReturn(true);

        assertThrows(EmailJaCadastradoException.class,
                () -> service.atualizar(1L, new AtualizarUsuarioCommand("Ana", "bruno@beca.com", null)));

        verify(repositorio, never()).salvar(any());
    }

    @Test
    void naoDeveAtualizarUsuarioInexistente() {
        when(repositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNaoEncontradoException.class,
                () -> service.atualizar(99L, new AtualizarUsuarioCommand("Ana", "ana@beca.com", null)));

        verify(repositorio, never()).salvar(any());
    }

    // ---------- excluir ----------

    @Test
    void deveExcluirUsuarioExistente() {
        when(repositorio.existePorId(1L)).thenReturn(true);

        service.excluir(1L);

        verify(repositorio).excluirPorId(1L);
    }

    @Test
    void naoDeveExcluirUsuarioInexistente() {
        when(repositorio.existePorId(99L)).thenReturn(false);

        assertThrows(UsuarioNaoEncontradoException.class, () -> service.excluir(99L));

        verify(repositorio, never()).excluirPorId(any());
    }

    // ---------- autenticar (tarefa 6.1) ----------

    @Test
    void autenticaComEmailNormalizadoESenhaQueConfere() {
        Usuario ana = Usuario.novo("Ana", "ana@beca.com", "hash-bcrypt");
        when(repositorio.buscarPorEmail("ana@beca.com")).thenReturn(Optional.of(ana));
        when(senhaEncoder.confere("Senha@123", "hash-bcrypt")).thenReturn(true);

        Usuario autenticado = service.autenticar("  Ana@Beca.COM ", "Senha@123");

        assertThat(autenticado).isSameAs(ana);
    }

    @Test
    void senhaErradaNaoAutentica() {
        Usuario ana = Usuario.novo("Ana", "ana@beca.com", "hash-bcrypt");
        when(repositorio.buscarPorEmail("ana@beca.com")).thenReturn(Optional.of(ana));
        when(senhaEncoder.confere("errada123", "hash-bcrypt")).thenReturn(false);

        assertThrows(CredenciaisInvalidasException.class, () -> service.autenticar("ana@beca.com", "errada123"));
    }

    @Test
    void emailInexistenteRespondeAMesmaMensagemDaSenhaErrada() {
        when(repositorio.buscarPorEmail("ninguem@beca.com")).thenReturn(Optional.empty());

        CredenciaisInvalidasException erro = assertThrows(CredenciaisInvalidasException.class,
                () -> service.autenticar("ninguem@beca.com", "Senha@123"));

        assertThat(erro.getMessage()).isEqualTo(CredenciaisInvalidasException.MENSAGEM);
        verifyNoInteractions(senhaEncoder);
    }

    @Test
    void senhaVaziaOuAcimaDe72BytesNemChegaAoBanco() {
        assertThrows(CredenciaisInvalidasException.class, () -> service.autenticar("ana@beca.com", ""));
        assertThrows(CredenciaisInvalidasException.class, () -> service.autenticar("ana@beca.com", "é".repeat(40)));
        assertThrows(CredenciaisInvalidasException.class, () -> service.autenticar(" ", "Senha@123"));

        verifyNoInteractions(repositorio, senhaEncoder);
    }
}
