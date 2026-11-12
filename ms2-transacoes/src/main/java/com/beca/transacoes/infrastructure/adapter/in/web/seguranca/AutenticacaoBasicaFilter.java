package com.beca.transacoes.infrastructure.adapter.in.web.seguranca;

import com.beca.transacoes.application.port.out.AutenticacaoPort;
import com.beca.transacoes.application.port.out.FalhaIntegracaoUsuariosException;
import com.beca.transacoes.application.port.out.ServicoUsuariosIndisponivelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Autenticação básica do MS2 (tarefa 6.2): toda requisição traz "Authorization: Basic base64(email:senha)",
 * e o MS2 confere no MS1 pelo nome do serviço. Se conferir, o id do usuário fica no atributo
 * {@link #ATRIBUTO_USUARIO} da requisição, para os controllers checarem o dono (tarefa 6.3).
 * A documentação e o health ficam abertos (tarefa 6.4).
 */
public class AutenticacaoBasicaFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "beca.usuarioAutenticadoId";

    static final String SEM_CREDENCIAIS =
            "Autenticação obrigatória: envie o cabeçalho Authorization: Basic com o e-mail e a senha.";
    static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";
    static final String USUARIOS_INDISPONIVEL =
            "Serviço de usuários indisponível para autenticar; tente novamente em instantes.";

    /** Rotas que não pedem login: documentação da API e health check do Compose. */
    static final List<String> ROTAS_ABERTAS =
            List.of("/swagger-ui.html", "/swagger-ui/", "/v3/api-docs", "/actuator/health");

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoBasicaFilter.class);
    private static final String PREFIXO_BASIC = "basic ";

    private final AutenticacaoPort autenticacao;
    private final ObjectMapper json;

    public AutenticacaoBasicaFilter(AutenticacaoPort autenticacao, ObjectMapper json) {
        this.autenticacao = autenticacao;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return ROTAS_ABERTAS.stream().anyMatch(caminho::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String[]> credenciais = lerCredenciais(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (credenciais.isEmpty()) {
            responder(request, response, HttpStatus.UNAUTHORIZED, SEM_CREDENCIAIS);
            return;
        }

        Optional<Long> usuarioId;
        try {
            usuarioId = autenticacao.autenticar(credenciais.get()[0], credenciais.get()[1]);
        } catch (ServicoUsuariosIndisponivelException | FalhaIntegracaoUsuariosException e) {
            log.warn("Não foi possível autenticar no MS1: {}", e.getClass().getSimpleName());
            responder(request, response, HttpStatus.SERVICE_UNAVAILABLE, USUARIOS_INDISPONIVEL);
            return;
        }
        if (usuarioId.isEmpty()) {
            responder(request, response, HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
            return;
        }

        request.setAttribute(ATRIBUTO_USUARIO, usuarioId.get());
        chain.doFilter(request, response);
    }

    /** "Basic base64(email:senha)" → [email, senha]; qualquer formato diferente → vazio. */
    static Optional<String[]> lerCredenciais(String cabecalho) {
        if (cabecalho == null || cabecalho.length() <= PREFIXO_BASIC.length()
                || !cabecalho.regionMatches(true, 0, PREFIXO_BASIC, 0, PREFIXO_BASIC.length())) {
            return Optional.empty();
        }
        String decodificado;
        try {
            decodificado = new String(Base64.getDecoder().decode(cabecalho.substring(PREFIXO_BASIC.length()).trim()),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        int separador = decodificado.indexOf(':');
        if (separador <= 0 || separador == decodificado.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new String[]{decodificado.substring(0, separador), decodificado.substring(separador + 1)});
    }

    /** O filtro roda antes do Spring MVC, então monta o ProblemDetail à mão, no mesmo formato do handler. */
    private void responder(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                           String detalhe) throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", status.getReasonPhrase());
        corpo.put("status", status.value());
        corpo.put("detail", detalhe);
        corpo.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ms2-transacoes\", charset=\"UTF-8\"");
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), corpo);
    }
}
