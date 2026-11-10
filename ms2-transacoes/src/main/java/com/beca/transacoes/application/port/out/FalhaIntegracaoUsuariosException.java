package com.beca.transacoes.application.port.out;

/**
 * O MS1 respondeu, mas com um erro 4xx inesperado (diferente de 404). Não é indisponibilidade:
 * é um problema no contrato entre os serviços, que precisa ser investigado.
 */
public class FalhaIntegracaoUsuariosException extends RuntimeException {

    public FalhaIntegracaoUsuariosException(int status, Throwable causa) {
        super("O serviço de usuários respondeu com o status " + status + ".", causa);
    }
}
