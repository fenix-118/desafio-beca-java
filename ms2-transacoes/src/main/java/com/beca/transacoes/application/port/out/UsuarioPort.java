package com.beca.transacoes.application.port.out;

/** Consulta ao MS1 para saber se um usuário existe. O MS2 nunca lê as tabelas do MS1. */
public interface UsuarioPort {

    /**
     * @return true se o usuário existe no MS1, false se o MS1 responder que não existe
     * @throws ServicoUsuariosIndisponivelException se o MS1 não responder ou falhar
     */
    boolean existe(Long usuarioId);
}
