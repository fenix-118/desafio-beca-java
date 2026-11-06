package com.beca.transacoes.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoJpaRepository extends JpaRepository<TransacaoEntity, Long> {
}
