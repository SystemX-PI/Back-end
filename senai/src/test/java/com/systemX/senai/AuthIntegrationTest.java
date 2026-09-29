package com.systemX.senai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.systemX.senai.config.JwtService;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.UsuarioRepository;
import com.systemX.senai.service.EmailService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldAuthenticateAndReturnJwt() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Ana");
        usuario.setEmail("ana@teste.com");
        usuario.setSenha(passwordEncoder.encode("123456"));
        usuario.setTipo(TipoUsuario.ADMINISTRADOR);
        usuarioRepository.save(usuario);

        String payload = "{\"email\":\"ana@teste.com\",\"senha\":\"123456\"}";

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tipo").value("ADMINISTRADOR"));
    }

    @Test
    void shouldConfigureEmailAtRuntimeWithoutReturningAppPassword() throws Exception {
        Usuario admin = criarUsuario("admin-email@teste.com", TipoUsuario.ADMINISTRADOR);

        mockMvc.perform(put("/api/configuracoes/email")
            .header("Authorization", "Bearer " + jwtService.generate(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"remetente@gmail.com\",\"senhaAplicativo\":\"abcd efgh ijkl mnop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(true))
                .andExpect(jsonPath("$.email").value("remetente@gmail.com"))
                .andExpect(jsonPath("$.senhaAplicativo").doesNotExist());

        assertTrue(emailService.isEnabled());
    }

    @Test
    void shouldRequireAuthenticationToConfigureEmail() throws Exception {
        mockMvc.perform(put("/api/configuracoes/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"remetente@gmail.com\",\"senhaAplicativo\":\"abcd efgh ijkl mnop\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAdministratorRoleToConfigureEmail() throws Exception {
        Usuario cliente = criarUsuario("cliente-email@teste.com", TipoUsuario.CLIENTE);

        mockMvc.perform(put("/api/configuracoes/email")
            .header("Authorization", "Bearer " + jwtService.generate(cliente))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"remetente@gmail.com\",\"senhaAplicativo\":\"abcd efgh ijkl mnop\"}"))
                .andExpect(status().isForbidden());
    }

    private Usuario criarUsuario(String email, TipoUsuario tipo) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode("senha-teste"));
        usuario.setTipo(tipo);
        return usuarioRepository.save(usuario);
    }
}
