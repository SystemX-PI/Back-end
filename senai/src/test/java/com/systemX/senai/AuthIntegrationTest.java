package com.systemX.senai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.systemX.senai.entity.Assembleia;
import com.systemX.senai.entity.Consorcio;
import com.systemX.senai.entity.Cota;
import com.systemX.senai.entity.Grupo;
import com.systemX.senai.entity.TipoConsorcio;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.AssembleiaRepository;
import com.systemX.senai.repository.ConsorcioRepository;
import com.systemX.senai.repository.CotaRepository;
import com.systemX.senai.repository.GrupoRepository;
import com.systemX.senai.repository.TipoConsorcioRepository;
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

    @Autowired
    private TipoConsorcioRepository tipoConsorcioRepository;

    @Autowired
    private ConsorcioRepository consorcioRepository;

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private CotaRepository cotaRepository;

    @Autowired
    private AssembleiaRepository assembleiaRepository;

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

    @Test
    void shouldReceiveContemplationInQueueAndNotifyOnlyOnRequest() throws Exception {
        Usuario admin = criarUsuario("admin-fila@teste.com", TipoUsuario.ADMINISTRADOR);

        TipoConsorcio tipo = new TipoConsorcio();
        tipo.setNome("Veiculos fila");
        tipo = tipoConsorcioRepository.save(tipo);

        Consorcio consorcio = consorcioRepository.save(new Consorcio("Consorcio fila", tipo));

        Grupo grupo = new Grupo();
        grupo.setNumero("GRUPO-FILA");
        grupo.setConsorcio(consorcio);
        grupo = grupoRepository.save(grupo);

        Cota cota = new Cota();
        cota.setNumero("COTA-FILA");
        cota.setGrupo(grupo);
        cota.setValorCredito(new java.math.BigDecimal("50000.00"));
        cota = cotaRepository.save(cota);

        Assembleia assembleia = Assembleia.builder()
                .grupo(grupo)
                .data(java.time.LocalDate.of(2026, 9, 15))
                .build();
        assembleia = assembleiaRepository.save(assembleia);

        String payload = """
                {
                  "origem":"sistema-parceiro",
                  "idExterno":"CONT-FILA-001",
                  "nome":"Maria da Silva",
                  "email":"maria-fila@teste.com",
                  "telefone":"5511999999999",
                  "cotaId":%d,
                  "assembleiaId":%d,
                  "dataContemplacao":"2026-09-15",
                  "tipo":"SORTEIO",
                  "valorCredito":50000.00
                }
                """.formatted(cota.getId(), assembleia.getId());

        String response = mockMvc.perform(post("/api/integracoes/contemplacoes")
                .header("X-Integration-Key", "integration-local-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusProcesso").value("AGUARDANDO_COMUNICACAO"))
                .andExpect(jsonPath("$.statusComunicacao").value("PENDENTE"))
                .andExpect(jsonPath("$.statusWhatsApp").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();

        Number processoIdValue = com.jayway.jsonpath.JsonPath.read(response, "$.processoId");
        long processoId = processoIdValue.longValue();
        String token = jwtService.generate(admin);

        mockMvc.perform(get("/api/integracoes/comunicacoes/fila")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processoId").value(processoId))
                .andExpect(jsonPath("$[0].idExterno").value("CONT-FILA-001"))
                .andExpect(jsonPath("$[0].comunicacao.statusGeral").value("PENDENTE"));

        mockMvc.perform(post("/api/integracoes/processos/{processoId}/comunicacao/reenvio", processoId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processoId").value(processoId))
                .andExpect(jsonPath("$.statusGeral").value("PENDENTE"));
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
