package com.beca.usuarios.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Esquema do botão "Authorize": login básico com o e-mail e a senha do próprio usuário. */
    public static final String LOGIN_BASICO = "loginBasico";

    @Bean
    public OpenAPI ms1UsuariosOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MS1 - Usuários")
                        .description("CRUD de usuários e importação via CSV - API de Gestão Financeira. "
                                + "Cadastro, importação e login são abertos; consultar, alterar e excluir pedem o "
                                + "login do próprio usuário (Authorize).")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(LOGIN_BASICO, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("E-mail e senha do usuário")));
    }
}
