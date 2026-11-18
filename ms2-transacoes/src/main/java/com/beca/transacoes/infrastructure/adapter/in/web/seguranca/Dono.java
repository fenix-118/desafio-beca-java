package com.beca.transacoes.infrastructure.adapter.in.web.seguranca;

/** Autorização (tarefa 6.3): cada usuário só vê e registra as próprias transações. */
public final class Dono {

    private Dono() {
    }

    /**
     * @param usuarioAutenticado id que o filtro de autenticação guardou na requisição
     * @param usuarioDoRecurso   dono do que está sendo pedido (ex.: a transação buscada pelo id)
     * @throws NaoAutenticadoException se não houver usuário autenticado
     * @throws AcessoNegadoException   se o recurso for de outro usuário
     */
    public static void exigir(Long usuarioAutenticado, Long usuarioDoRecurso) {
        usuarioDoLogin(usuarioAutenticado);
        if (usuarioDoRecurso != null && !usuarioAutenticado.equals(usuarioDoRecurso)) {
            throw new AcessoNegadoException();
        }
    }

    /**
     * O usuário das rotas do MS2 vem sempre do login, nunca do corpo nem da query: assim não há como pedir
     * ou registrar algo em nome de outro.
     *
     * @return o id que o filtro de autenticação guardou na requisição
     * @throws NaoAutenticadoException se não houver usuário autenticado
     */
    public static Long usuarioDoLogin(Long usuarioAutenticado) {
        if (usuarioAutenticado == null) {
            throw new NaoAutenticadoException();
        }
        return usuarioAutenticado;
    }
}
