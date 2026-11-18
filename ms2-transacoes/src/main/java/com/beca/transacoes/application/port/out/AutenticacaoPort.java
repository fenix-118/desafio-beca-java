package com.beca.transacoes.application.port.out;

import java.util.Optional;

/** Confere e-mail e senha no MS1 (tarefa 6.2). O MS2 nunca guarda nem lê senhas. */
public interface AutenticacaoPort {

    /**
     * @return o id do usuário se as credenciais conferem, ou vazio se o MS1 responder que não conferem
     * @throws ServicoUsuariosIndisponivelException se o MS1 não responder, demorar ou falhar com 5xx
     * @throws FalhaIntegracaoUsuariosException     se o MS1 responder com outro 4xx
     */
    Optional<Long> autenticar(String email, String senha);
}
