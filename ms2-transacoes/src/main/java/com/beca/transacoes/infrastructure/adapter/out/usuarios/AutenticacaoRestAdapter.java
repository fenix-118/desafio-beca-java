package com.beca.transacoes.infrastructure.adapter.out.usuarios;

import com.beca.transacoes.application.port.out.AutenticacaoPort;
import com.beca.transacoes.application.port.out.FalhaIntegracaoUsuariosException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

/**
 * Pergunta ao MS1 se e-mail e senha conferem, pelo POST /autenticacao: 200 = confere (devolve o id),
 * 401 = não confere. Outro 4xx é erro de integração; MS1 fora do ar, tempo limite ou 5xx é indisponibilidade.
 * Nem o e-mail nem a senha vão para o log.
 */
public class AutenticacaoRestAdapter implements AutenticacaoPort {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoRestAdapter.class);

    private final RestClient ms1;

    public AutenticacaoRestAdapter(RestClient ms1) {
        this.ms1 = ms1;
    }

    @Override
    public Optional<Long> autenticar(String email, String senha) {
        try {
            RespostaAutenticacaoMs1 resposta = ms1.post()
                    .uri("/autenticacao")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "senha", senha))
                    .retrieve()
                    .body(RespostaAutenticacaoMs1.class);
            if (resposta == null || resposta.id() == null) {
                throw new ServicoUsuariosIndisponivelException("MS1 respondeu a autenticação sem o id do usuário.", null);
            }
            return Optional.of(resposta.id());
        } catch (HttpClientErrorException.Unauthorized e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            log.error("MS1 respondeu {} ao autenticar um usuário; verificar o contrato entre os serviços", status);
            throw new FalhaIntegracaoUsuariosException(status, e);
        } catch (RestClientException e) {
            throw new ServicoUsuariosIndisponivelException("MS1 indisponível ao autenticar o usuário.", e);
        }
    }
}
