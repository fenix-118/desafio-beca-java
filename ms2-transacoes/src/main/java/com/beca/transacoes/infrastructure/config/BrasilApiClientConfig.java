package com.beca.transacoes.infrastructure.config;

import com.beca.transacoes.application.port.out.CambioPort;
import com.beca.transacoes.infrastructure.adapter.out.brasilapi.BrasilApiCambioAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** Cliente HTTP da BrasilAPI (API pública, sem chave). A URL pode ser trocada por BRASILAPI_BASE_URL. */
@Configuration
public class BrasilApiClientConfig {

    @Bean
    public CambioPort cambioPort(RestClient.Builder builder,
                                 @Value("${brasilapi.base-url}") String baseUrl,
                                 @Value("${brasilapi.timeout-ms}") int timeoutMs,
                                 Clock relogio) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutMs);
        fabrica.setReadTimeout(timeoutMs);

        RestClient brasilApi = builder
                .baseUrl(baseUrl)
                .requestFactory(fabrica)
                .build();
        return new BrasilApiCambioAdapter(brasilApi, relogio);
    }
}
