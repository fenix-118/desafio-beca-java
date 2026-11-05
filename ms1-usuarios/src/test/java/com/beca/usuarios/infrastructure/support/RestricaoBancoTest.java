package com.beca.usuarios.infrastructure.support;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class RestricaoBancoTest {

    private static DataIntegrityViolationException violacao(String nomeDaRestricao) {
        ConstraintViolationException causa = new ConstraintViolationException(
                "duplicate key", new SQLException("Key (email)=(ana@beca.com) already exists."), nomeDaRestricao);
        return new DataIntegrityViolationException("could not execute statement", causa);
    }

    @Test
    void deveEncontrarONomeDaRestricaoDentroDaCadeiaDeCausas() {
        assertThat(RestricaoBanco.nome(violacao("uk_usuarios_email"))).isEqualTo("uk_usuarios_email");
    }

    @Test
    void deveRetornarDesconhecidaQuandoOBancoNaoInformaARestricao() {
        DataIntegrityViolationException semCausa = new DataIntegrityViolationException("erro");

        assertThat(RestricaoBanco.nome(semCausa)).isEqualTo(RestricaoBanco.DESCONHECIDA);
    }

    @Test
    void deveReconhecerARestricaoDeEmailSemDiferenciarMaiusculas() {
        assertThat(RestricaoBanco.ehEmailUnico(violacao("UK_USUARIOS_EMAIL"))).isTrue();
    }

    @Test
    void naoDeveConfundirOutraRestricaoComADeEmail() {
        assertThat(RestricaoBanco.ehEmailUnico(violacao("uk_usuarios_cpf"))).isFalse();
    }

    @Test
    void naoDeveExporAMensagemDoBancoComDadosDoUsuario() {
        assertThat(RestricaoBanco.nome(violacao("uk_usuarios_email"))).doesNotContain("ana@beca.com");
    }
}