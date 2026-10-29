package com.beca.usuarios.infrastructure.adapter.in.csv;

public class ArquivoCsvInvalidoException extends RuntimeException {

    public ArquivoCsvInvalidoException(String mensagem) {
        super(mensagem);
    }
}
