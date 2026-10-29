package com.beca.usuarios.infrastructure.config;

import com.beca.usuarios.application.port.out.SenhaEncoderPort;
import com.beca.usuarios.application.port.out.UsuarioRepositoryPort;
import com.beca.usuarios.application.service.UsuarioService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public UsuarioService usuarioService(UsuarioRepositoryPort repositorio, SenhaEncoderPort senhaEncoder) {
        return new UsuarioService(repositorio, senhaEncoder);
    }
}
