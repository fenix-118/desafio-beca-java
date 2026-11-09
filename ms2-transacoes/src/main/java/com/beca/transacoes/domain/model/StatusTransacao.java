package com.beca.transacoes.domain.model;

public enum StatusTransacao {
    /** Só existe em memória, entre a solicitação e a avaliação. Nunca é gravado. */
    PENDENTE,
    APROVADA,
    REJEITADA
}
