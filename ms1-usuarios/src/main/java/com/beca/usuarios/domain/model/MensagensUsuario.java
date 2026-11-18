package com.beca.usuarios.domain.model;

/**
 * Mensagens de validação do usuário num lugar só (tarefa 8.1). O {@code @Valid} dos DTOs e o
 * service usam as mesmas constantes, então a mesma regra responde o mesmo texto pela API,
 * pela importação CSV e pelo domínio.
 */
public final class MensagensUsuario {

    public static final int NOME_MAX = 120;
    public static final int EMAIL_MAX = 160;
    public static final int SENHA_MIN = 8;
    /** Limite do BCrypt: ele considera no máximo 72 bytes da senha. */
    public static final int SENHA_MAX_BYTES = 72;

    public static final String NOME_OBRIGATORIO = "O campo 'nome' é obrigatório.";
    public static final String NOME_TAMANHO = "O campo 'nome' deve ter no máximo 120 caracteres.";
    public static final String EMAIL_OBRIGATORIO = "O campo 'email' é obrigatório.";
    public static final String EMAIL_INVALIDO = "E-mail inválido.";
    public static final String EMAIL_TAMANHO = "O campo 'email' deve ter no máximo 160 caracteres.";
    public static final String SENHA_OBRIGATORIA = "O campo 'senha' é obrigatório.";
    public static final String SENHA_CURTA = "A senha deve ter no mínimo 8 caracteres.";
    public static final String SENHA_LONGA = "A senha é longa demais: o limite é de 72 bytes, e letras acentuadas "
            + "ou símbolos ocupam mais de um byte cada.";

    private MensagensUsuario() {
    }
}
