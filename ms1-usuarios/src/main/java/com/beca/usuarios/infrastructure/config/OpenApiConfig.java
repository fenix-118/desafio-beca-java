package com.beca.usuarios.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ms1UsuariosOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MS1 - Usuários")
                .description("CRUD de usuários e importação via CSV - API de Gestão Financeira")
                .version("v1"));
    }
}