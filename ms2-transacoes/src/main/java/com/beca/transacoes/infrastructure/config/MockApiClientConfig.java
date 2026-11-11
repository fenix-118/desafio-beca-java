package com.beca.transacoes.infrastructure.config;

import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.infrastructure.adapter.out.mockapi.MockApiContaAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP da API Mock. A URL vem só de MOCKAPI_BASE_URL (arquivo .env ou variável de ambiente):
 * ela não aparece no código nem no repositório.
 */
@Configuration
public class MockApiClientConfig {

    @Bean
    public ContaPort contaPort(RestClient.Builder builder,
                               @Value("${mockapi.base-url}") String baseUrl,
                               @Value("${mockapi.timeout-ms}") int timeoutMs) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Defina MOCKAPI_BASE_URL no arquivo .env (veja o .env.example) para o MS2 consultar as contas.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutMs);
        fabrica.setReadTimeout(timeoutMs);

        RestClient mockApi = builder
                .baseUrl(baseUrl)
                .requestFactory(fabrica)
                .build();
        return new MockApiContaAdapter(mockApi);
    }
}
