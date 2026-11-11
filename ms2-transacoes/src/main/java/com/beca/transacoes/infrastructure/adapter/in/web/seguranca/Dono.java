package com.beca.transacoes.infrastructure.adapter.in.web.seguranca;

/** Autorização (tarefa 6.3): cada usuário só vê e registra as próprias transações. */
public final class Dono {

    private Dono() {
    }

    /**
     * @param usuarioAutenticado id que o filtro de autenticação guardou na requisição
     * @param usuarioDoRecurso   dono do que está sendo pedido (parâmetro, corpo ou transação)
     * @throws NaoAutenticadoException se não houver usuário autenticado
     * @throws AcessoNegadoException   se o recurso for de outro usuário
     */
    public static void exigir(Long usuarioAutenticado, Long usuarioDoRecurso) {
        if (usuarioAutenticado == null) {
            throw new NaoAutenticadoException();
        }
        if (usuarioDoRecurso != null && !usuarioAutenticado.equals(usuarioDoRecurso)) {
            throw new AcessoNegadoException();
        }
    }
}
