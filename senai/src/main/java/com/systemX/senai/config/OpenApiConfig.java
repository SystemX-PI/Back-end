package com.systemX.senai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI documentacaoSystemX() {
        return new OpenAPI()
                .info(new Info()
                        .title("API SystemX")
                        .version("v1")
                        .description("Use Autenticação para obter uma sessão e informe o token de acesso em Authorize nas operações protegidas. Plataforma reúne conta e canais; Assinaturas reúne a gestão do plano. Eventos do Stripe são chamados pelo provedor com assinatura própria."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}