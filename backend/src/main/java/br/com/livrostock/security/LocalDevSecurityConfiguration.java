package br.com.livrostock.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Maintains backward compatibility with the local v0.2 workflow.
 * This mode is NOT suitable for public exposure.
 */
@Configuration
@Profile("!secure")
public class LocalDevSecurityConfiguration {
    @Bean
    SecurityFilterChain localDevelopmentSecurity(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
