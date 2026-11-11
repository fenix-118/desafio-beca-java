package com.beca.transacoes.infrastructure.config;

import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.TransacaoRepositoryPort;
import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.application.service.SaldoService;
import com.beca.transacoes.application.service.TransacaoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BeanConfig {

    /** Relógio injetado para os testes conseguirem fixar a data e a hora. */
    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    public TransacaoService transacaoService(TransacaoRepositoryPort repositorio, ContaPort conta,
                                             UsuarioPort usuarios, Clock relogio) {
        return new TransacaoService(repositorio, conta, usuarios, relogio);
    }

    @Bean
    public SaldoService saldoService(UsuarioPort usuarios, ContaPort conta, TransacaoRepositoryPort repositorio,
                                     Clock relogio) {
        return new SaldoService(usuarios, conta, repositorio, relogio);
    }
}
