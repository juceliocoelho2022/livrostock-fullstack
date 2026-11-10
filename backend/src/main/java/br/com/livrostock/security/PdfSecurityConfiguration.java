package br.com.livrostock.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import org.springframework.security.core.userdetails.*;
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
    private final String bibliotecario;
    private final String senhaBibliotecario;
    private final String leitor;
    private final String senhaLeitor;

    public PdfSecurityConfiguration(
            @Value("${LIVROSTOCK_JWT_SECRET:}") String jwtSecret,
            @Value("${LIVROSTOCK_LIBRARIAN_USER:bibliotecario}") String bibliotecario,
            @Value("${LIVROSTOCK_LIBRARIAN_PASSWORD:}") String senhaBibliotecario,
            @Value("${LIVROSTOCK_READER_USER:leitor}") String leitor,
            @Value("${LIVROSTOCK_READER_PASSWORD:}") String senhaLeitor) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Defina LIVROSTOCK_JWT_SECRET com pelo menos 32 bytes");
        }
        if (senhaBibliotecario.length() < 12 || senhaLeitor.length() < 12) {
            throw new IllegalStateException("Defina senhas de 12 ou mais caracteres para bibliotecário e leitor");
        }
        if (bibliotecario.isBlank() || leitor.isBlank() || bibliotecario.equals(leitor)) {
            throw new IllegalStateException("Os usuários de bibliotecário e leitor devem ser distintos");
        }
        this.jwtSecret = jwtSecret;
        this.bibliotecario = bibliotecario;
        this.senhaBibliotecario = senhaBibliotecario;
        this.leitor = leitor;
        this.senhaLeitor = senhaLeitor;
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
                .requestMatchers(HttpMethod.POST, "/api/v1/livros/*/exemplares-digitais").hasRole("BIBLIOTECARIO")
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/livros/*/exemplares-digitais",
                        "/api/v1/livros/*/exemplares-digitais/*/arquivo")
                    .hasAnyRole("BIBLIOTECARIO", "LEITOR")
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        UserDetails bibliotecarioDetails = User.withUsername(bibliotecario)
                .password(encoder.encode(senhaBibliotecario)).roles("BIBLIOTECARIO").build();
        UserDetails leitorDetails = User.withUsername(leitor)
                .password(encoder.encode(senhaLeitor)).roles("LEITOR").build();
        return new InMemoryUserDetailsManager(bibliotecarioDetails, leitorDetails);
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwordEncoder) {
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
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
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
