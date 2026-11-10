package com.beca.transacoes.infrastructure.adapter.out.usuarios;

import com.beca.transacoes.application.port.out.FalhaIntegracaoUsuariosException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.beca.transacoes.application.port.out.UsuarioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pergunta ao MS1 se o usuário existe, pelo GET /usuarios/{id}: 200 = existe, 404 = não existe.
 * Outro 4xx é erro de integração (FalhaIntegracaoUsuariosException, com log.error do status).
 * MS1 fora do ar, tempo limite ou 5xx vira ServicoUsuariosIndisponivelException.
 */
public class UsuarioRestAdapter implements UsuarioPort {

    private static final Logger log = LoggerFactory.getLogger(UsuarioRestAdapter.class);

    private final RestClient ms1;

    public UsuarioRestAdapter(RestClient ms1) {
        this.ms1 = ms1;
    }

    @Override
    public boolean existe(Long usuarioId) {
        try {
            ms1.get()
                    .uri("/usuarios/{id}", usuarioId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            log.error("MS1 respondeu {} ao consultar um usuário; verificar o contrato entre os serviços", status);
            throw new FalhaIntegracaoUsuariosException(status, e);
        } catch (RestClientException e) {
            throw new ServicoUsuariosIndisponivelException("MS1 indisponível ao consultar o usuário.", e);
        }
    }
}
