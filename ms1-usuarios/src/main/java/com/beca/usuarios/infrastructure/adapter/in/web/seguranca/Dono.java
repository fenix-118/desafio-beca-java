package com.beca.usuarios.infrastructure.adapter.in.web.seguranca;

/** Autorização no MS1: cada usuário só vê, altera e exclui a própria conta. */
public final class Dono {

    private Dono() {
    }

    /**
     * @throws NaoAutenticadoException se não houver usuário autenticado
     * @throws AcessoNegadoException   se a conta pedida for de outro usuário
     */
    public static void exigir(Long usuarioAutenticado, Long idDaConta) {
        if (usuarioAutenticado == null) {
            throw new NaoAutenticadoException();
        }
        if (!usuarioAutenticado.equals(idDaConta)) {
            throw new AcessoNegadoException();
        }
    }
}
