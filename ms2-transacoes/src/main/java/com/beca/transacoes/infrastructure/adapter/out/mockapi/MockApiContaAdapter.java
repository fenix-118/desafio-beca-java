package com.beca.transacoes.infrastructure.adapter.out.mockapi;

import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.application.port.out.ServicoSaldoIndisponivelException;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.model.SituacaoConta;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Lê a conta do usuário no recurso "contas" da API Mock.
 *
 * O filtro ?usuarioId= da Mock procura por "contém" (usuarioId=3 também traz o 13), por isso o
 * usuarioId exato é conferido aqui. Quando nada combina, a Mock responde 404 "Not found": isso é
 * "usuário sem conta", não falha. Qualquer outro erro vira ServicoSaldoIndisponivelException.
 */
public class MockApiContaAdapter implements ContaPort {

    private static final ParameterizedTypeReference<List<ContaMockApi>> LISTA_DE_CONTAS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient mockApi;

    public MockApiContaAdapter(RestClient mockApi) {
        this.mockApi = mockApi;
    }

    @Override
    public Optional<SituacaoConta> consultar(Long usuarioId) {
        return buscar(usuarioId).map(MockApiContaAdapter::paraSituacao);
    }

    Optional<ContaMockApi> buscar(Long usuarioId) {
        List<ContaMockApi> contas;
        try {
            contas = mockApi.get()
                    .uri(uri -> uri.path("/contas").queryParam("usuarioId", usuarioId).build())
                    .retrieve()
                    .body(LISTA_DE_CONTAS);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ServicoSaldoIndisponivelException("API Mock indisponível ao consultar a conta.", e);
        }
        if (contas == null) {
            return Optional.empty();
        }
        return contas.stream()
                .filter(c -> usuarioId.equals(c.usuarioId()))
                .findFirst();
    }

    private static SituacaoConta paraSituacao(ContaMockApi c) {
        try {
            return new SituacaoConta(c.saldo(), c.limitePorTransacao(), c.limiteDiario());
        } catch (DadosInvalidosException e) {
            // Saldo ou limite ausente/negativo na Mock: não dá para avaliar com segurança.
            throw new ServicoSaldoIndisponivelException("A API Mock devolveu dados de conta inválidos.", e);
        }
    }
}
