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
 * Somente eventos de leitura/download de PDF são registrados após a resposta.
 * Escritas de catálogo/estoque/contas e upload de PDF geram auditoria na
 * própria transação de negócio; não duplicar eventos neste interceptor.
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
        if (auditoria != null) {
            registry.addInterceptor(new RegistroLeituraInterceptor(auditoria))
                    .addPathPatterns("/api/v1/livros/*/exemplares-digitais/*/arquivo");
        }
    }

    private record RegistroLeituraInterceptor(AuditoriaService service) implements HandlerInterceptor {
        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            if (ex != null || response.getStatus() != 200 || !"GET".equals(request.getMethod())) return;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return;
            String acao = "true".equalsIgnoreCase(request.getParameter("download"))
                    ? "PDF_DOWNLOAD" : "PDF_LEITURA";
            String rota = request.getRequestURI();
            if (rota.length() > 255) rota = rota.substring(0, 255);
            try {
                service.registrar(auth.getName(), acao, "GET", rota, 200);
            } catch (RuntimeException e) {
                LOG.error("Falha ao persistir auditoria da leitura/download", e);
            }
        }
    }
}
