package com.beca.usuarios.application.port.out;

public interface SenhaEncoderPort {

    String codificar(String senhaPura);

    /** Confere a senha digitada com o hash guardado, sem nunca decodificar o hash. */
    boolean confere(String senhaPura, String senhaHash);
}
