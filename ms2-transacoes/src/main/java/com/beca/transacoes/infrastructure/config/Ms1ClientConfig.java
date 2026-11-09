package com.beca.transacoes.infrastructure.config;

import com.beca.transacoes.application.port.out.UsuarioPort;
import com.beca.transacoes.infrastructure.adapter.out.usuarios.UsuarioRestAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP do MS1. A URL vem de MS1_BASE_URL; no Compose, é o nome do serviço. */
@Configuration
public class Ms1ClientConfig {

    @Bean
    public UsuarioPort usuarioPort(RestClient.Builder builder,
                                   @Value("${ms1.base-url}") String baseUrl,
                                   @Value("${ms1.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutMs);
        fabrica.setReadTimeout(timeoutMs);

        RestClient ms1 = builder
                .baseUrl(baseUrl)
                .requestFactory(fabrica)
                .build();
        return new UsuarioRestAdapter(ms1);
    }
}
