package com.beca.transacoes.infrastructure.adapter.in.web;

import com.beca.transacoes.application.port.out.FalhaIntegracaoUsuariosException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.beca.transacoes.domain.exception.DadosInvalidosException;
import com.beca.transacoes.domain.exception.TransacaoNaoEncontradaException;
import com.beca.transacoes.domain.exception.UsuarioNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String USUARIOS_INDISPONIVEL =
            "Serviço de usuários indisponível; tente novamente em instantes.";

    static final String FALHA_INTEGRACAO_USUARIOS =
            "Erro na integração com o serviço de usuários; a transação não foi registrada.";

    static final String CORPO_ILEGIVEL =
            "Corpo da requisição inválido: confira o JSON e os valores aceitos em 'tipo' e 'categoria'.";

    @ExceptionHandler(TransacaoNaoEncontradaException.class)
    public ProblemDetail naoEncontrada(TransacaoNaoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UsuarioNaoEncontradoException.class)
    public ProblemDetail usuarioNaoEncontrado(UsuarioNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** MS1 fora do ar: nada é gravado e o cliente pode tentar de novo. O detalhe técnico fica só no log. */
    @ExceptionHandler(ServicoUsuariosIndisponivelException.class)
    public ProblemDetail usuariosIndisponivel(ServicoUsuariosIndisponivelException ex) {
        log.warn("MS1 indisponível: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, USUARIOS_INDISPONIVEL);
    }

    /** 4xx inesperado do MS1: o log.error com o status já foi feito no adapter. */
    @ExceptionHandler(FalhaIntegracaoUsuariosException.class)
    public ProblemDetail falhaIntegracaoUsuarios(FalhaIntegracaoUsuariosException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, FALHA_INTEGRACAO_USUARIOS);
    }

    @ExceptionHandler(DadosInvalidosException.class)
    public ProblemDetail dadosInvalidos(DadosInvalidosException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** JSON malformado ou enum desconhecido (ex.: "tipo": "PIX"). A mensagem do Jackson não é exposta. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail corpoIlegivel(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, CORPO_ILEGIVEL);
    }

    /** Parâmetro de rota ou de consulta com tipo errado (ex.: /transacoes/abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "O parâmetro '" + ex.getName() + "' tem um valor inválido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail parametroAusente(MissingServletRequestParameterException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "O parâmetro '" + ex.getParameterName() + "' é obrigatório.");
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
