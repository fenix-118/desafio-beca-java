package com.beca.transacoes.infrastructure.adapter.out.brasilapi;

import com.beca.transacoes.application.port.out.CambioPort;
import com.beca.transacoes.domain.model.Cotacao;
import com.beca.transacoes.domain.model.MoedaEstrangeira;
import com.beca.transacoes.infrastructure.adapter.out.brasilapi.RespostaCotacaoBrasilApi.Boletim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Busca a cotação na BrasilAPI (GET /cambio/v1/cotacao/{moeda}/{data}), seguindo o que a API faz de verdade:
 * - o dia de hoje responde 400 ("NO_TODAY_DATE"), então hoje ou futuro consulta o dia anterior;
 * - fim de semana e feriado devolvem a cotação do dia útil anterior, então a data guardada é a da resposta;
 * - usa o boletim "FECHAMENTO PTAX" (valor de venda), a referência oficial do Banco Central.
 * Cotação de um dia passado não muda: fica em cache na memória para não repetir a chamada.
 * Falhas também ficam em cache, pelo mesmo tempo da pausa (5 minutos por padrão):
 * - sem conexão, tempo limite ou 5xx: a BrasilAPI inteira fica em pausa, e nenhuma moeda é consultada;
 * - recusa (4xx), resposta sem cotação ou num formato inesperado: só aquela moeda e dia deixam de ser consultados.
 * Nos dois casos a conversão sai indisponível na hora, sem repetir a chamada a cada transação.
 */
public class BrasilApiCambioAdapter implements CambioPort {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final String FECHAMENTO_PTAX = "FECHAMENTO PTAX";

    private static final Logger log = LoggerFactory.getLogger(BrasilApiCambioAdapter.class);

    static final Duration PAUSA_PADRAO = Duration.ofMinutes(5);

    private final RestClient brasilApi;
    private final Clock relogio;
    private final Duration pausaAposFalha;
    private final Map<String, Cotacao> cache = new ConcurrentHashMap<>();
    /** Moeda e dia que falharam, e até quando não vale a pena consultar de novo (cache de falhas). */
    private final Map<String, Instant> falhas = new ConcurrentHashMap<>();
    /** Até quando não vale a pena chamar a BrasilAPI, depois de uma falha de conexão. */
    private volatile Instant pausadaAte = Instant.MIN;

    public BrasilApiCambioAdapter(RestClient brasilApi, Clock relogio) {
        this(brasilApi, relogio, PAUSA_PADRAO);
    }

    public BrasilApiCambioAdapter(RestClient brasilApi, Clock relogio, Duration pausaAposFalha) {
        this.brasilApi = brasilApi;
        this.relogio = relogio;
        this.pausaAposFalha = pausaAposFalha;
    }

    @Override
    public Optional<Cotacao> cotacao(MoedaEstrangeira moeda, LocalDate dataDaTransacao) {
        LocalDate dataConsultada = dataParaConsultar(dataDaTransacao);
        String chave = moeda + "|" + dataConsultada;

        Cotacao emCache = cache.get(chave);
        if (emCache != null) {
            return Optional.of(emCache);
        }

        Instant agora = relogio.instant();
        if (agora.isBefore(pausadaAte)) {
            return Optional.empty();
        }
        Instant falhouAte = falhas.get(chave);
        if (falhouAte != null && agora.isBefore(falhouAte)) {
            return Optional.empty();
        }

        Optional<Cotacao> cotacao = consultar(moeda, dataConsultada);
        if (cotacao.isPresent()) {
            cache.put(chave, cotacao.get());
            falhas.remove(chave);
        } else {
            falhas.put(chave, agora.plus(pausaAposFalha));
        }
        return cotacao;
    }

    /** A BrasilAPI não aceita o dia de hoje: hoje ou futuro vira o dia anterior a hoje. */
    LocalDate dataParaConsultar(LocalDate dataDaTransacao) {
        LocalDate hoje = LocalDate.now(relogio.withZone(FUSO));
        return dataDaTransacao.isBefore(hoje) ? dataDaTransacao : hoje.minusDays(1);
    }

    private Optional<Cotacao> consultar(MoedaEstrangeira moeda, LocalDate data) {
        RespostaCotacaoBrasilApi resposta;
        try {
            resposta = brasilApi.get()
                    .uri("/cambio/v1/cotacao/{moeda}/{data}", moeda.name(), data.toString())
                    .retrieve()
                    .body(RespostaCotacaoBrasilApi.class);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // Fora do ar, tempo limite ou 5xx: pausa as próximas consultas para não repetir a espera.
            pausadaAte = relogio.instant().plus(pausaAposFalha);
            log.warn("BrasilAPI indisponível para {} em {}: conversão indisponível e consultas em pausa por {} ({})",
                    moeda, data, pausaAposFalha, causaRaiz(e));
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("BrasilAPI recusou a consulta de {} em {}: conversão indisponível ({})",
                    moeda, data, causaRaiz(e));
            return Optional.empty();
        }
        try {
            return paraCotacao(moeda, resposta);
        } catch (RuntimeException e) {
            // Resposta num formato inesperado: o câmbio é informativo e não pode quebrar a transação.
            log.warn("BrasilAPI respondeu num formato inesperado para {}: conversão indisponível ({})",
                    moeda, causaRaiz(e));
            return Optional.empty();
        }
    }

    private static Optional<Cotacao> paraCotacao(MoedaEstrangeira moeda, RespostaCotacaoBrasilApi resposta) {
        if (resposta == null || resposta.data() == null || resposta.cotacoes() == null || resposta.cotacoes().isEmpty()) {
            log.warn("BrasilAPI respondeu sem cotação para {}: conversão indisponível", moeda);
            return Optional.empty();
        }
        Boletim boletim = escolherBoletim(resposta.cotacoes());
        if (boletim.cotacaoVenda() == null || boletim.cotacaoVenda().signum() <= 0) {
            log.warn("BrasilAPI respondeu cotação inválida para {}: conversão indisponível", moeda);
            return Optional.empty();
        }
        return Optional.of(new Cotacao(moeda, boletim.cotacaoVenda(), resposta.data()));
    }

    /** O fechamento PTAX quando existe; senão, o último boletim do dia. */
    static Boletim escolherBoletim(List<Boletim> boletins) {
        return boletins.stream()
                .filter(b -> FECHAMENTO_PTAX.equalsIgnoreCase(b.tipoBoletim()))
                .findFirst()
                .orElse(boletins.get(boletins.size() - 1));
    }

    static String causaRaiz(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz.getClass().getSimpleName();
    }
}
