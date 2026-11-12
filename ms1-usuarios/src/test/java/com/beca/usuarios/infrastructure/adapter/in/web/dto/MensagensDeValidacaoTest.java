package com.beca.usuarios.infrastructure.adapter.in.web.dto;

import com.beca.usuarios.application.port.in.CriarUsuarioUseCase.CriarUsuarioCommand;
import com.beca.usuarios.application.port.out.SenhaEncoderPort;
import com.beca.usuarios.application.port.out.UsuarioRepositoryPort;
import com.beca.usuarios.application.service.UsuarioService;
import com.beca.usuarios.domain.exception.DadosInvalidosException;
import com.beca.usuarios.domain.model.MensagensUsuario;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tarefa 8.1: a mesma regra responde o mesmo texto no {@code @Valid} do DTO e no service
 * (que também atende a importação CSV, onde não há DTO).
 */
class MensagensDeValidacaoTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    private final UsuarioService service =
            new UsuarioService(mock(UsuarioRepositoryPort.class), mock(SenhaEncoderPort.class));

    @BeforeAll
    static void criarValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        fabrica.close();
    }

    private static Set<String> mensagensDoDto(CriarUsuarioRequest request) {
        return validador.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private void assertMesmaMensagem(String nome, String email, String senha, String esperada) {
        assertThat(mensagensDoDto(new CriarUsuarioRequest(nome, email, senha))).contains(esperada);
        assertThatThrownBy(() -> service.criar(new CriarUsuarioCommand(nome, email, senha)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(esperada);
    }

    @Test
    void nomeObrigatorio() {
        assertMesmaMensagem(" ", "ana@beca.com", "Senha@123", MensagensUsuario.NOME_OBRIGATORIO);
    }

    @Test
    void nomeLongoDemais() {
        assertMesmaMensagem("a".repeat(121), "ana@beca.com", "Senha@123", MensagensUsuario.NOME_TAMANHO);
    }

    @Test
    void emailObrigatorio() {
        assertMesmaMensagem("Ana", " ", "Senha@123", MensagensUsuario.EMAIL_OBRIGATORIO);
    }

    @Test
    void emailInvalido() {
        assertMesmaMensagem("Ana", "ana.beca.com", "Senha@123", MensagensUsuario.EMAIL_INVALIDO);
    }

    @Test
    void senhaObrigatoria() {
        assertMesmaMensagem("Ana", "ana@beca.com", " ", MensagensUsuario.SENHA_OBRIGATORIA);
    }

    @Test
    void senhaCurta() {
        assertMesmaMensagem("Ana", "ana@beca.com", "123", MensagensUsuario.SENHA_CURTA);
    }

    @Test
    void senhaAcimaDe72BytesSoOServiceDetectaEComMensagemPropria() {
        // O @Size conta caracteres; o limite do BCrypt é em bytes, então quem barra é o service.
        String senha = "é".repeat(40);
        assertThat(mensagensDoDto(new CriarUsuarioRequest("Ana", "ana@beca.com", senha))).isEmpty();
        assertThatThrownBy(() -> service.criar(new CriarUsuarioCommand("Ana", "ana@beca.com", senha)))
                .hasMessage(MensagensUsuario.SENHA_LONGA);
    }

    @Test
    void osLimitesDasMensagensBatemComOsNumeros() {
        assertThat(MensagensUsuario.NOME_TAMANHO).contains(String.valueOf(MensagensUsuario.NOME_MAX));
        assertThat(MensagensUsuario.EMAIL_TAMANHO).contains(String.valueOf(MensagensUsuario.EMAIL_MAX));
        assertThat(MensagensUsuario.SENHA_CURTA).contains(String.valueOf(MensagensUsuario.SENHA_MIN));
        assertThat(MensagensUsuario.SENHA_LONGA).contains(String.valueOf(MensagensUsuario.SENHA_MAX_BYTES));
    }
}
