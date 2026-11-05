package com.beca.usuarios.infrastructure.adapter.out.persistence;

import com.beca.usuarios.application.port.out.UsuarioRepositoryPort;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import com.beca.usuarios.domain.model.Usuario;
import com.beca.usuarios.infrastructure.support.RestricaoBanco;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UsuarioPersistenceAdapter implements UsuarioRepositoryPort {

    private final UsuarioJpaRepository jpa;

    public UsuarioPersistenceAdapter(UsuarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        try {
            return paraDominio(jpa.saveAndFlush(paraEntidade(usuario)));
        } catch (DataIntegrityViolationException e) {
            // Corrida entre duas requisições com o mesmo e-mail: a unique constraint do banco decide.
            if (RestricaoBanco.ehEmailUnico(e)) {
                throw new EmailJaCadastradoException();
            }
            // Outra restrição: segue para o handler global, que responde com mensagem neutra.
            throw e;
        }
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return jpa.findById(id).map(this::paraDominio);
    }

    @Override
    public List<Usuario> listarTodos() {
        return jpa.findAll().stream().map(this::paraDominio).toList();
    }

    @Override
    public boolean existePorEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public boolean existePorId(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public void excluirPorId(Long id) {
        jpa.deleteById(id);
    }

    private UsuarioEntity paraEntidade(Usuario u) {
        return new UsuarioEntity(u.getId(), u.getNome(), u.getEmail(), u.getSenhaHash(), u.getCriadoEm());
    }

    private Usuario paraDominio(UsuarioEntity e) {
        return Usuario.reconstituir(e.getId(), e.getNome(), e.getEmail(), e.getSenhaHash(), e.getCriadoEm());
    }
}