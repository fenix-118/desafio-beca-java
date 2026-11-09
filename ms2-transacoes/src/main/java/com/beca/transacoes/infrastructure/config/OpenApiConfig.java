package com.beca.transacoes.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ms2TransacoesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MS2 - Transações")
                .description("Registro e consulta de transações financeiras - API de Gestão Financeira")
                .version("v1"));
    }
}
