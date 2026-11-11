package br.com.livrostock.security;

import br.com.livrostock.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra somente operações concluídas com sucesso em modo seguro.
 * O registro é independente da transação original; não substitui auditoria forense.
 */
@Configuration
@Profile("secure")
public class AuditoriaMvcConfiguration implements WebMvcConfigurer {
    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaMvcConfiguration.class);
    private final ObjectProvider<AuditoriaService> service;

    public AuditoriaMvcConfiguration(ObjectProvider<AuditoriaService> service) {
        this.service = service;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        AuditoriaService auditoria = service.getIfAvailable();
        // No @WebMvcTest o repositório JPA não é carregado; a segurança pode ser testada isoladamente.
        if (auditoria != null) {
            registry.addInterceptor(new RegistroInterceptor(auditoria))
                    .addPathPatterns("/api/v1/livros", "/api/v1/livros/**");
        }
    }

    private record RegistroInterceptor(AuditoriaService service) implements HandlerInterceptor {
        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            int status = response.getStatus();
            if (ex != null || status < 200 || status >= 300) return;

            String metodo = request.getMethod();
            String rota = request.getRequestURI();
            String acao;

            if ("GET".equals(metodo)) {
                if (!rota.matches("^/api/v1/livros/\\d+/exemplares-digitais/\\d+/arquivo$")) return;
                acao = "true".equalsIgnoreCase(request.getParameter("download")) ? "PDF_DOWNLOAD" : "PDF_LEITURA";
            } else if ("POST".equals(metodo) && rota.matches("^/api/v1/livros/\\d+/movimentacoes$")) {
                acao = "ESTOQUE_MOVIMENTAR";
            } else if ("POST".equals(metodo) && rota.matches("^/api/v1/livros/\\d+/exemplares-digitais$")) {
                acao = "PDF_UPLOAD";
            } else if ("POST".equals(metodo) && "/api/v1/livros".equals(rota)) {
                acao = "LIVRO_CRIAR";
            } else if ("PUT".equals(metodo) && rota.matches("^/api/v1/livros/\\d+$")) {
                acao = "LIVRO_EDITAR";
            } else if ("DELETE".equals(metodo) && rota.matches("^/api/v1/livros/\\d+$")) {
                acao = "LIVRO_DESATIVAR";
            } else {
                return;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return;
            // Nunca registrar headers Authorization, passwords ou corpo da requisição.
            String usuario = auth.getName();
            if (usuario.length() > 120) usuario = usuario.substring(0, 120);
            if (rota.length() > 255) rota = rota.substring(0, 255);
            try {
                service.registrar(usuario, acao, metodo, rota, status);
            } catch (RuntimeException e) {
                // Operação já pode ter sido confirmada; erro de auditoria precisa de alerta operacional.
                LOG.error("Falha ao persistir auditoria da operacao {}", acao, e);
            }
        }
    }
}
