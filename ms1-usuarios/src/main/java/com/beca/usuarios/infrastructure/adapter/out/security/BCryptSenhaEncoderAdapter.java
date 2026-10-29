package com.beca.usuarios.infrastructure.adapter.out.security;

import com.beca.usuarios.application.port.out.SenhaEncoderPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptSenhaEncoderAdapter implements SenhaEncoderPort {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String codificar(String senhaPura) {
        return encoder.encode(senhaPura);
    }
}
