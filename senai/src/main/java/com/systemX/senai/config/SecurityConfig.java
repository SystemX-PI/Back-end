package com.systemX.senai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/platform/**").hasAnyRole("ADMINISTRADOR", "CLIENTE", "USUARIO", "FUNCIONARIO")
                    .requestMatchers("/api/portal/**").hasRole("CLIENTE")
                    .requestMatchers(HttpMethod.POST, "/api/integracoes/contemplacoes").permitAll()
                    .requestMatchers("/api/configuracoes/**", "/api/integracoes/whatsapp/**").hasRole("ADMINISTRADOR")
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole("ADMINISTRADOR")
                    .requestMatchers(HttpMethod.GET, "/api/consorcios/**", "/api/cotas/**", "/api/grupos/**", "/api/tipos-consorcio/**")
                        .hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                    .requestMatchers("/api/consorcios/**", "/api/cotas/**", "/api/grupos/**", "/api/tipos-consorcio/**")
                        .hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                    .anyRequest().authenticated())                    .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(401)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
