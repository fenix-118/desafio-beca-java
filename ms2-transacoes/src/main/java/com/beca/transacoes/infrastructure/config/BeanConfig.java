package com.beca.transacoes.infrastructure.config;

import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.application.service.TransacaoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public TransacaoService transacaoService(TransacaoRepositoryPort repositorio) {
        return new TransacaoService(repositorio);
    }
}
