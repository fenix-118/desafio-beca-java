package com.beca.usuarios.infrastructure.adapter.in.web;

import com.beca.usuarios.domain.exception.CredenciaisInvalidasException;
import com.beca.usuarios.domain.exception.DadosInvalidosException;
import com.beca.usuarios.domain.exception.EmailJaCadastradoException;
import com.beca.usuarios.domain.exception.UsuarioNaoEncontradoException;
import com.beca.usuarios.infrastructure.adapter.in.csv.ArquivoCsvInvalidoException;
import com.beca.usuarios.infrastructure.adapter.in.web.seguranca.AcessoNegadoException;
import com.beca.usuarios.infrastructure.adapter.in.web.seguranca.NaoAutenticadoException;
import com.beca.usuarios.infrastructure.support.RestricaoBanco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    static final String MENSAGEM_CONFLITO_DADOS = "Conflito de dados: o registro viola uma restrição do banco.";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Sem log do e-mail nem da senha: a resposta é sempre a mesma mensagem. */
    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ProblemDetail credenciaisInvalidas(CredenciaisInvalidasException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(NaoAutenticadoException.class)
    public ProblemDetail naoAutenticado(NaoAutenticadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ProblemDetail acessoNegado(AcessoNegadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UsuarioNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(UsuarioNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ProblemDetail conflito(EmailJaCadastradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Violações que o adapter não reconhece (o e-mail duplicado já vira EmailJaCadastradoException lá).
     * O log grava só o nome da restrição: a mensagem do PostgreSQL traz os valores da linha.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridade(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade no banco: constraint={}", RestricaoBanco.nome(ex));
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, MENSAGEM_CONFLITO_DADOS);
    }

    @ExceptionHandler(DadosInvalidosException.class)
    public ProblemDetail dadosInvalidos(DadosInvalidosException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ArquivoCsvInvalidoException.class)
    public ProblemDetail arquivoInvalido(ArquivoCsvInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail arquivoGrande(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "O arquivo excede o tamanho máximo permitido (1 MB).");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        problema.setProperty("campos", campos);
        return problema;
    }
}