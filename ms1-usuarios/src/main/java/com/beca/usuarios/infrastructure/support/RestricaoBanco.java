package com.beca.usuarios.infrastructure.support;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifica qual restrição do banco foi violada, sem expor a mensagem do banco
 * (que no PostgreSQL inclui os valores da linha, como o e-mail do usuário).
 */
public final class RestricaoBanco {

    /** Nome da restrição de e-mail único, usado na entidade JPA. */
    public static final String EMAIL_UNICO = "uk_usuarios_email";

    static final String DESCONHECIDA = "desconhecida";

    private RestricaoBanco() {
    }

    /** Devolve o nome da restrição violada, ou "desconhecida" se o banco não informou. */
    public static String nome(DataIntegrityViolationException ex) {
        Throwable causa = ex;
        while (causa != null) {
            if (causa instanceof ConstraintViolationException violacao
                    && violacao.getConstraintName() != null) {
                return violacao.getConstraintName();
            }
            causa = causa.getCause();
        }
        return DESCONHECIDA;
    }

    public static boolean ehEmailUnico(DataIntegrityViolationException ex) {
        return EMAIL_UNICO.equalsIgnoreCase(nome(ex));
    }
}