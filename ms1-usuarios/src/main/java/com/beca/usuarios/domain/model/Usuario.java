package com.beca.usuarios.domain.model;

import com.beca.usuarios.domain.exception.DadosInvalidosException;

import java.time.Instant;

public class Usuario {

    private final Long id;
    private String nome;
    private String email;
    private String senhaHash;
    private final Instant criadoEm;

    private Usuario(Long id, String nome, String email, String senhaHash, Instant criadoEm) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public static Usuario novo(String nome, String email, String senhaHash) {
        return new Usuario(
                null,
                exigirTexto(nome, "nome"),
                exigirTexto(email, "email"),
                exigirTexto(senhaHash, "senha"),
                Instant.now());
    }

    public static Usuario reconstituir(Long id, String nome, String email, String senhaHash, Instant criadoEm) {
        return new Usuario(id, nome, email, senhaHash, criadoEm);
    }

    public void atualizarDados(String nome, String email) {
        this.nome = exigirTexto(nome, "nome");
        this.email = exigirTexto(email, "email");
    }

    public void alterarSenhaHash(String senhaHash) {
        this.senhaHash = exigirTexto(senhaHash, "senha");
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new DadosInvalidosException("O campo '" + campo + "' é obrigatório.");
        }
        return valor.trim();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Instant getCriadoEm() { return criadoEm; }
}
