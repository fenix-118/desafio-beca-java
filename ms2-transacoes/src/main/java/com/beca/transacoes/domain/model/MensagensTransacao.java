package com.beca.transacoes.domain.model;

/**
 * Mensagens de validação usadas pelo domínio e pelos DTOs da API.
 * Ficam num lugar só para que a mesma regra responda com o mesmo texto em qualquer camada.
 */
public final class MensagensTransacao {

    public static final String USUARIO_OBRIGATORIO = "O campo 'usuarioId' é obrigatório.";
    public static final String TIPO_OBRIGATORIO = "O campo 'tipo' é obrigatório.";
    public static final String VALOR_OBRIGATORIO = "O campo 'valor' é obrigatório.";
    public static final String VALOR_POSITIVO = "O campo 'valor' deve ser maior que zero.";
    public static final String VALOR_FORMATO = "O campo 'valor' aceita até 13 dígitos inteiros e 2 casas decimais.";
    public static final String DESTINO_INVALIDO = "O campo 'usuarioDestinoId' deve ser um id válido.";
    public static final String DESCRICAO_TAMANHO = "O campo 'descricao' aceita no máximo 140 caracteres.";

    private MensagensTransacao() {
    }
}
