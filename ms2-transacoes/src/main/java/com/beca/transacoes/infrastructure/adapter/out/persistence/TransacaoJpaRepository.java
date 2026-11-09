package com.beca.transacoes.infrastructure.adapter.out.persistence;

import com.beca.transacoes.domain.model.StatusTransacao;
import com.beca.transacoes.domain.model.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TransacaoJpaRepository extends JpaRepository<TransacaoEntity, Long> {

    /** Devolve null quando não há nenhuma linha; o adapter troca por zero. */
    @Query("""
            select sum(t.valor) from TransacaoEntity t
            where t.usuarioId = :usuarioId
              and t.status = :status
              and t.tipo in :tipos
              and t.criadaEm >= :inicio
              and t.criadaEm < :fim
            """)
    BigDecimal somarValores(@Param("usuarioId") Long usuarioId,
                            @Param("status") StatusTransacao status,
                            @Param("tipos") Collection<TipoTransacao> tipos,
                            @Param("inicio") Instant inicio,
                            @Param("fim") Instant fim);

    @Query("""
            select t from TransacaoEntity t
            where t.usuarioId = :usuarioId
              and t.tipo in :tipos
              and t.criadaEm >= :inicio
              and t.criadaEm < :fim
            order by t.criadaEm desc, t.id desc
            """)
    List<TransacaoEntity> listar(@Param("usuarioId") Long usuarioId,
                                 @Param("tipos") Collection<TipoTransacao> tipos,
                                 @Param("inicio") Instant inicio,
                                 @Param("fim") Instant fim);
}
