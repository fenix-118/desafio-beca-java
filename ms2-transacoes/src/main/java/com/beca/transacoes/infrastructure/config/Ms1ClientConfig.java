package com.beca.transacoes.infrastructure.config;

import com.beca.transacoes.application.port.out.AutenticacaoPort;
import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.infrastructure.adapter.out.usuarios.AutenticacaoRestAdapter;
import com.beca.transacoes.infrastructure.adapter.out.usuarios.UsuarioRestAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP do MS1. A URL vem de MS1_BASE_URL; no Compose, é o nome do serviço. */
@Configuration
public class Ms1ClientConfig {

    /** Cabeçalho com o token de serviço: o MS1 só deixa o MS2 consultar usuários com ele. */
    static final String CABECALHO_SERVICO = "X-Servico-Token";

    @Bean
    public UsuarioPort usuarioPort(RestClient.Builder builder,
                                   @Value("${ms1.base-url}") String baseUrl,
                                   @Value("${ms1.timeout-ms}") int timeoutMs,
                                   @Value("${ms1.token-servico:}") String tokenServico) {
        if (tokenServico.isBlank()) {
            throw new IllegalStateException(
                    "Defina SERVICO_TOKEN no arquivo .env (o mesmo do MS1): sem ele o MS1 recusa as consultas de usuário.");
        }
        RestClient ms1 = ms1(builder, baseUrl, timeoutMs).mutate()
                .defaultHeader(CABECALHO_SERVICO, tokenServico)
                .build();
        return new UsuarioRestAdapter(ms1);
    }

    @Bean
    public AutenticacaoPort autenticacaoPort(RestClient.Builder builder,
                                             @Value("${ms1.base-url}") String baseUrl,
                                             @Value("${ms1.timeout-ms}") int timeoutMs) {
        return new AutenticacaoRestAdapter(ms1(builder, baseUrl, timeoutMs));
    }

    private static RestClient ms1(RestClient.Builder builder, String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutMs);
        fabrica.setReadTimeout(timeoutMs);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(fabrica)
                .build();
    }
}
