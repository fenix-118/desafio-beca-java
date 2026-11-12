package com.beca.transacoes.infrastructure.config;

import com.beca.transacoes.application.port.out.AutenticacaoPort;
import com.beca.transacoes.infrastructure.adapter.in.web.seguranca.AutenticacaoBasicaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Liga o filtro de autenticação básica em todas as rotas (as abertas são tratadas no próprio filtro). */
@Configuration
public class SegurancaConfig {

    @Bean
    public FilterRegistrationBean<AutenticacaoBasicaFilter> autenticacaoBasica(AutenticacaoPort autenticacao,
                                                                               ObjectMapper json) {
        FilterRegistrationBean<AutenticacaoBasicaFilter> registro =
                new FilterRegistrationBean<>(new AutenticacaoBasicaFilter(autenticacao, json));
        registro.addUrlPatterns("/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registro;
    }
}
