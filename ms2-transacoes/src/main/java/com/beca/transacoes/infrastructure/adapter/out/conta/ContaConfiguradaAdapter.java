package com.beca.transacoes.infrastructure.adapter.out.conta;

import com.beca.transacoes.application.port.out.ContaPort;
import com.beca.transacoes.domain.model.SituacaoConta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * PROVISÓRIO até o pedido 2: devolve a mesma conta, configurada no application.yml, para todos os usuários.
 * No pedido 2 este adaptador é apagado e entra o da API Mock, sem mudar domínio nem serviço:
 * é a troca de adaptador que a arquitetura hexagonal permite.
 */
@Component
public class ContaConfiguradaAdapter implements ContaPort {

    private final SituacaoConta situacao;

    public ContaConfiguradaAdapter(@Value("${conta.padrao.saldo}") BigDecimal saldo,
                                   @Value("${conta.padrao.limite-por-transacao}") BigDecimal limitePorTransacao,
                                   @Value("${conta.padrao.limite-diario}") BigDecimal limiteDiario) {
        this.situacao = new SituacaoConta(saldo, limitePorTransacao, limiteDiario);
    }

    @Override
    public SituacaoConta consultar(Long usuarioId) {
        return situacao;
    }
}
