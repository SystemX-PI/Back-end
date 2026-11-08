package com.systemX.senai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String ROLE_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ROLE_FUNCIONARIO = "FUNCIONARIO";
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Accept", "Authorization", "Content-Type", "X-Integration-Key"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/google/link").authenticated()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/assistente/**").hasAnyRole(ROLE_ADMINISTRADOR, ROLE_FUNCIONARIO)
                    .requestMatchers(HttpMethod.POST, "/api/platform/webhooks/stripe").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/widget/session", "/api/widget/chat").permitAll()
                    .requestMatchers("/api/platform/clients/**").hasRole(ROLE_ADMINISTRADOR)
                    .requestMatchers("/api/platform/operation-channels/**").hasRole(ROLE_ADMINISTRADOR)
                    .requestMatchers("/api/platform/**").hasAnyRole(ROLE_ADMINISTRADOR, "CLIENTE", "USUARIO")
                    .requestMatchers("/api/portal/**").hasRole("CLIENTE")
                    .requestMatchers(HttpMethod.POST, "/api/integracoes/contemplacoes").permitAll()
                    .requestMatchers("/api/configuracoes/**", "/api/integracoes/whatsapp/**").hasRole(ROLE_ADMINISTRADOR)
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole(ROLE_ADMINISTRADOR)
                    .requestMatchers(HttpMethod.GET, "/api/consorcios/**", "/api/cotas/**", "/api/grupos/**", "/api/tipos-consorcio/**")
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_FUNCIONARIO)
                    .requestMatchers("/api/consorcios/**", "/api/cotas/**", "/api/grupos/**", "/api/tipos-consorcio/**")
                        .hasRole(ROLE_ADMINISTRADOR)
                    .requestMatchers("/api/**").hasAnyRole(ROLE_ADMINISTRADOR, ROLE_FUNCIONARIO)
                    .anyRequest().authenticated())                    .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(401)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
