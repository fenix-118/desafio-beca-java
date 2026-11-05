package com.beca.usuarios.infrastructure.adapter.out.persistence;

import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import com.beca.usuarios.domain.model.Usuario;
import com.beca.usuarios.infrastructure.support.RestricaoBanco;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioPersistenceAdapterTest {

    @Mock
    private UsuarioJpaRepository jpa;

    @InjectMocks
    private UsuarioPersistenceAdapter adapter;

    private static DataIntegrityViolationException violacao(String nomeDaRestricao) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key"), nomeDaRestricao));
    }

    @Test
    void deveSalvarEConverterParaODominio() {
        Instant criadoEm = Instant.parse("2026-11-05T12:00:00Z");
        when(jpa.saveAndFlush(any()))
                .thenReturn(new UsuarioEntity(1L, "Ana", "ana@beca.com", "hash", criadoEm));

        Usuario salvo = adapter.salvar(Usuario.novo("Ana", "ana@beca.com", "hash"));

        assertThat(salvo.getId()).isEqualTo(1L);
        assertThat(salvo.getEmail()).isEqualTo("ana@beca.com");
        assertThat(salvo.getCriadoEm()).isEqualTo(criadoEm);
    }

    @Test
    void violacaoDaRestricaoDeEmailViraEmailJaCadastrado() {
        when(jpa.saveAndFlush(any())).thenThrow(violacao(RestricaoBanco.EMAIL_UNICO));

        assertThrows(EmailJaCadastradoException.class,
                () -> adapter.salvar(Usuario.novo("Ana", "ana@beca.com", "hash")));
    }

    @Test
    void violacaoDeOutraRestricaoSegueSemSerConvertida() {
        DataIntegrityViolationException outra = violacao("uk_usuarios_outra");
        when(jpa.saveAndFlush(any())).thenThrow(outra);

        DataIntegrityViolationException lancada = assertThrows(DataIntegrityViolationException.class,
                () -> adapter.salvar(Usuario.novo("Ana", "ana@beca.com", "hash")));

        assertThat(lancada).isSameAs(outra);
    }
}