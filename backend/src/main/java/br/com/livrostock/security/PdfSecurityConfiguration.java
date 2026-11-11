package br.com.livrostock.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import br.com.livrostock.domain.Usuario;
import br.com.livrostock.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.core.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("secure")
public class PdfSecurityConfiguration {
    static final String ISSUER = "livrostock";
    private final String jwtSecret;
    public PdfSecurityConfiguration(@Value("${LIVROSTOCK_JWT_SECRET:}") String jwtSecret) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Defina LIVROSTOCK_JWT_SECRET com pelo menos 32 bytes");
        }
        this.jwtSecret = jwtSecret;
    }

    @Bean
    SecurityFilterChain pdfSecurityFilterChain(HttpSecurity http,
                                                 JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/mode", "/actuator/health", "/actuator/info").permitAll()
                // Importação digital deve existir somente no perfil secure e requer Bibliotecário.
                .requestMatchers(HttpMethod.POST, "/api/v1/importacoes-digitais/drive").hasRole("BIBLIOTECARIO")
                // Inventário e PDFs: somente o bibliotecário pode modificar dados.
                .requestMatchers(HttpMethod.POST, "/api/v1/livros", "/api/v1/livros/*/movimentacoes",
                        "/api/v1/livros/*/exemplares-digitais").hasRole("BIBLIOTECARIO")
                .requestMatchers(HttpMethod.PUT, "/api/v1/livros/*").hasRole("BIBLIOTECARIO")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/livros/*").hasRole("BIBLIOTECARIO")
                // Apenas bibliotecário pode auditar hashes e disponibilidade dos arquivos.
                .requestMatchers(HttpMethod.GET, "/api/v1/acervo-digital/integridade").hasRole("BIBLIOTECARIO")
                // O catálogo global de PDFs exige JWT; metadados nunca são públicos.
                .requestMatchers(HttpMethod.GET, "/api/v1/acervo-digital").hasAnyRole("BIBLIOTECARIO", "LEITOR")
                // Consulta do catálogo, movimentações e documentos para ambos os perfis.
                .requestMatchers(HttpMethod.GET, "/api/v1/auditoria", "/api/v1/usuarios").hasRole("BIBLIOTECARIO")
                .requestMatchers(HttpMethod.POST, "/api/v1/usuarios").hasRole("BIBLIOTECARIO")
                .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/*/perfil",
                        "/api/v1/usuarios/*/senha").hasRole("BIBLIOTECARIO")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/usuarios/*/status").hasRole("BIBLIOTECARIO")
                .requestMatchers(HttpMethod.GET, "/api/v1/livros", "/api/v1/livros/**")
                    .hasAnyRole("BIBLIOTECARIO", "LEITOR")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    AuthenticationManager authenticationManager(org.springframework.security.core.userdetails.UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(UsuarioRepository usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> contaAtiva = jwt -> {
            try {
                Optional<Usuario> registro = usuarios.findByNomeUsuario(jwt.getSubject());
                Number versao = jwt.getClaim("ver");
                List<String> roles = jwt.getClaimAsStringList("roles");
                if (registro.isPresent() && registro.get().isAtivo()
                        && versao != null && versao.intValue() == registro.get().getVersaoToken()
                        && roles != null && roles.size() == 1
                        && roles.contains(registro.get().getPerfil().name())) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (RuntimeException ignored) {
                // Se não conseguir consultar o banco, nega a autenticação.
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Conta desativada, alterada ou inexistente", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), contaAtiva));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter granted = new JwtGrantedAuthoritiesConverter();
        granted.setAuthoritiesClaimName("roles");
        granted.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(granted);
        return converter;
    }
}
