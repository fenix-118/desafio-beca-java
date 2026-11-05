package com.beca.usuarios.application.service;

import com.beca.usuarios.application.port.in.CriarUsuarioUseCase;
import com.beca.usuarios.application.port.in.CriarUsuarioUseCase.CriarUsuarioCommand;
import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.FalhaImportacao;
import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.LinhaImportacao;
import com.beca.usuarios.application.port.in.ImportarUsuariosUseCase.ResultadoImportacao;
import com.beca.usuarios.application.port.out.SenhaEncoderPort;
import com.beca.usuarios.application.port.out.UsuarioRepositoryPort;
import com.beca.usuarios.domain.exception.DadosInvalidosException;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportacaoUsuariosServiceTest {

    @Mock
    private CriarUsuarioUseCase criarUsuario;

    @InjectMocks
    private ImportacaoUsuariosService service;

    @Test
    void deveImportarTodasAsLinhasValidas() {
        List<LinhaImportacao> linhas = List.of(
                new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"),
                new LinhaImportacao(3, "Bruno", "bruno@beca.com", "Senha@456"),
                new LinhaImportacao(4, "Carla", "carla@beca.com", "Senha@789"));

        ResultadoImportacao resultado = service.importar(linhas);

        assertThat(resultado.total()).isEqualTo(3);
        assertThat(resultado.criados()).isEqualTo(3);
        assertThat(resultado.falhas()).isEmpty();
        verify(criarUsuario, times(3)).criar(any());
    }

    @Test
    void deveRepassarOsDadosDaLinhaParaOCadastro() {
        service.importar(List.of(new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123")));

        ArgumentCaptor<CriarUsuarioCommand> comando = ArgumentCaptor.forClass(CriarUsuarioCommand.class);
        verify(criarUsuario).criar(comando.capture());
        assertThat(comando.getValue().nome()).isEqualTo("Ana");
        assertThat(comando.getValue().email()).isEqualTo("ana@beca.com");
        assertThat(comando.getValue().senha()).isEqualTo("Senha@123");
    }

    @Test
    void deveRegistrarFalhaPorLinhaSemInterromperAImportacao() {
        when(criarUsuario.criar(any())).thenAnswer(invocacao -> {
            CriarUsuarioCommand comando = invocacao.getArgument(0);
            if (comando.email().equals("duplicado@beca.com")) {
                throw new EmailJaCadastradoException();
            }
            if (comando.email().equals("email-invalido")) {
                throw new DadosInvalidosException("E-mail inválido.");
            }
            return null;
        });
        List<LinhaImportacao> linhas = List.of(
                new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"),
                new LinhaImportacao(3, "Bruno", "duplicado@beca.com", "Senha@456"),
                new LinhaImportacao(4, "Carla", "email-invalido", "Senha@789"),
                new LinhaImportacao(5, "Diego", "diego@beca.com", "Senha@000"));

        ResultadoImportacao resultado = service.importar(linhas);

        assertThat(resultado.total()).isEqualTo(4);
        assertThat(resultado.criados()).isEqualTo(2);
        assertThat(resultado.falhas()).containsExactly(
                new FalhaImportacao(3, EmailJaCadastradoException.MENSAGEM),
                new FalhaImportacao(4, "E-mail inválido."));
        verify(criarUsuario, times(4)).criar(any());
    }

    @Test
    void deveRetornarResultadoZeradoParaListaVazia() {
        ResultadoImportacao resultado = service.importar(List.of());

        assertThat(resultado.total()).isZero();
        assertThat(resultado.criados()).isZero();
        assertThat(resultado.falhas()).isEmpty();
        verifyNoInteractions(criarUsuario);
    }

    @Test
    void naoDeveEsconderErroInesperado() {
        when(criarUsuario.criar(any())).thenThrow(new IllegalStateException("falha inesperada"));

        assertThrows(IllegalStateException.class,
                () -> service.importar(List.of(new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"))));
    }

    @Test
    void senhaAcimaDe72BytesViraFalhaDaLinhaSemInterromperOLote() {
        UsuarioRepositoryPort repositorio = mock(UsuarioRepositoryPort.class);
        SenhaEncoderPort encoder = mock(SenhaEncoderPort.class);
        when(repositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(encoder.codificar(anyString())).thenReturn("hash");
        ImportacaoUsuariosService importacao =
                new ImportacaoUsuariosService(new UsuarioService(repositorio, encoder));

        ResultadoImportacao resultado = importacao.importar(List.of(
                new LinhaImportacao(2, "Ana", "ana@beca.com", "Senha@123"),
                new LinhaImportacao(3, "Bruno", "bruno@beca.com", "é".repeat(40)),
                new LinhaImportacao(4, "Carla", "carla@beca.com", "Senha@789")));

        assertThat(resultado.total()).isEqualTo(3);
        assertThat(resultado.criados()).isEqualTo(2);
        assertThat(resultado.falhas()).extracting(FalhaImportacao::numeroLinha).containsExactly(3);
    }
}