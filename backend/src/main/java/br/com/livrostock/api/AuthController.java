package br.com.livrostock.api;

import br.com.livrostock.domain.Usuario;
import br.com.livrostock.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@Profile("secure")
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;
    private final UsuarioRepository usuarios;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder encoder,
                          UsuarioRepository usuarios) {
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
        this.usuarios = usuarios;
    }

    public record LoginRequest(@NotBlank String usuario, @NotBlank String senha) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, List<String> roles) { }

    @GetMapping("/mode")
    public Map<String, Object> mode() {
        return Map.of("enabled", true, "strategy", "jwt");
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest input) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(input.usuario(), input.senha()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }
        Usuario conta = usuarios.findByNomeUsuario(auth.getName())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));
        Instant now = Instant.now();
        List<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5)).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("livrostock")
                .issuedAt(now)
                .expiresAt(now.plus(30, ChronoUnit.MINUTES))
                .subject(auth.getName())
                .claim("roles", roles)
                .claim("ver", conta.getVersaoToken())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(new TokenResponse(token, "Bearer", 1800, roles));
    }
}
