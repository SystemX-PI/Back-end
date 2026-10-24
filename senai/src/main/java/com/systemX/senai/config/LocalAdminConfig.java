package com.systemX.senai.config;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.UsuarioRepository;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("local")
public class LocalAdminConfig {
    @Bean
    CommandLineRunner criarAdministradorLocal(UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${LOCAL_ADMIN_EMAIL:}") String email, @Value("${LOCAL_ADMIN_PASSWORD:}") String senha) {
        return args -> {
            if (email.isBlank() || senha.isBlank()) return;
            Usuario existente = usuarios.findByEmail(email).orElse(null);
            // Never reset a configured administrator's password on restart.
            if (existente != null && existente.getTipo() == TipoUsuario.ADMINISTRADOR) return;
            if (senha.length() < 10)
                throw new IllegalArgumentException("Administrador local: use senha com pelo menos 10 caracteres.");
            Usuario admin = existente == null ? new Usuario() : existente;
            if (existente == null) { admin.setNome("Administrador local"); admin.setEmail(email); }
            else admin.invalidarSessoes();
            admin.setSenha(encoder.encode(senha));
            admin.setTipo(TipoUsuario.ADMINISTRADOR);
            admin.setAtivo(true);
            usuarios.save(admin);
        };
    }
}
