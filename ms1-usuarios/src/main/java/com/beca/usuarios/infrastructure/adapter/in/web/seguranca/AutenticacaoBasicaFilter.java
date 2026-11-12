package com.beca.usuarios.infrastructure.adapter.in.web.seguranca;

import com.beca.usuarios.application.port.in.AutenticarUsuarioUseCase;
import com.beca.usuarios.domain.exception.CredenciaisInvalidasException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Protege as rotas de um usuário (GET, PUT e DELETE /usuarios/{id}). Entram:
 * - o próprio usuário, com "Authorization: Basic base64(email:senha)" conferido aqui no MS1;
 * - o MS2, só no GET, com o token de serviço no cabeçalho {@link #CABECALHO_SERVICO} (conferir dono e destino).
 * Cadastro, importação, login, documentação e health não passam por aqui (tarefa 6.4).
 */
public class AutenticacaoBasicaFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "beca.usuarioAutenticadoId";
    public static final String ATRIBUTO_SERVICO = "beca.chamadaDeServico";
    public static final String CABECALHO_SERVICO = "X-Servico-Token";

    static final String SEM_CREDENCIAIS =
            "Autenticação obrigatória: envie o cabeçalho Authorization: Basic com o e-mail e a senha.";

    /** Só /usuarios/{id numérico}: /usuarios (cadastro) e /usuarios/importacao ficam abertos. */
    private static final Pattern ROTA_DE_USUARIO = Pattern.compile("^/usuarios/\\d+$");
    private static final String PREFIXO_BASIC = "basic ";

    private final AutenticarUsuarioUseCase autenticar;
    private final byte[] tokenServico;
    private final ObjectMapper json;

    public AutenticacaoBasicaFilter(AutenticarUsuarioUseCase autenticar, String tokenServico, ObjectMapper json) {
        this.autenticar = autenticar;
        this.tokenServico = tokenServico.getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return !ROTA_DE_USUARIO.matcher(caminho).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("GET".equals(request.getMethod()) && tokenDeServicoConfere(request.getHeader(CABECALHO_SERVICO))) {
            request.setAttribute(ATRIBUTO_SERVICO, Boolean.TRUE);
            chain.doFilter(request, response);
            return;
        }

        Optional<String[]> credenciais = lerCredenciais(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (credenciais.isEmpty()) {
            responder401(request, response, SEM_CREDENCIAIS);
            return;
        }
        try {
            Long id = autenticar.autenticar(credenciais.get()[0], credenciais.get()[1]).getId();
            request.setAttribute(ATRIBUTO_USUARIO, id);
        } catch (CredenciaisInvalidasException e) {
            responder401(request, response, CredenciaisInvalidasException.MENSAGEM);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Comparação em tempo constante, para o tempo de resposta não revelar o token. */
    private boolean tokenDeServicoConfere(String recebido) {
        return recebido != null && !recebido.isEmpty()
                && MessageDigest.isEqual(tokenServico, recebido.getBytes(StandardCharsets.UTF_8));
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

    private void responder401(HttpServletRequest request, HttpServletResponse response, String detalhe)
            throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        corpo.put("status", HttpStatus.UNAUTHORIZED.value());
        corpo.put("detail", detalhe);
        corpo.put("instance", request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ms1-usuarios\", charset=\"UTF-8\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), corpo);
    }
}
