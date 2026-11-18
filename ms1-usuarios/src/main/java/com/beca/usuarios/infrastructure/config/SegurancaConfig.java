package com.beca.usuarios.infrastructure.config;

import com.beca.usuarios.application.port.in.AutenticarUsuarioUseCase;
import com.beca.usuarios.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Liga o filtro que protege GET, PUT e DELETE /usuarios/{id}. */
@Configuration
public class SegurancaConfig {

    @Bean
    public FilterRegistrationBean<AutenticacaoBasicaFilter> autenticacaoBasica(
            AutenticarUsuarioUseCase autenticar,
            @Value("${servico.token:}") String tokenServico,
            ObjectMapper json) {
        if (tokenServico.isBlank()) {
            throw new IllegalStateException(
                    "Defina SERVICO_TOKEN no arquivo .env: é o token que o MS2 usa para consultar usuários no MS1.");
        }
        FilterRegistrationBean<AutenticacaoBasicaFilter> registro =
                new FilterRegistrationBean<>(new AutenticacaoBasicaFilter(autenticar, tokenServico, json));
        registro.addUrlPatterns("/usuarios/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registro;
    }
}
