package com.beca.transacoes.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Nome do esquema de segurança: o botão "Authorize" do Swagger usa o login básico (e-mail e senha do MS1). */
    static final String LOGIN_BASICO = "loginBasico";

    @Bean
    public OpenAPI ms2TransacoesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MS2 - Transações")
                        .description("Registro e consulta de transações financeiras - API de Gestão Financeira. "
                                + "Todas as rotas pedem login básico com o e-mail e a senha cadastrados no MS1: "
                                + "clique em Authorize.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(LOGIN_BASICO, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("E-mail e senha do usuário no MS1")))
                .addSecurityItem(new SecurityRequirement().addList(LOGIN_BASICO));
    }
}
