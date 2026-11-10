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

    @org.junit.jupiter.api.AfterEach
    void limparConfiguracaoEmail() { emailService.configure("", ""); }

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
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@teste.com\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getContentAsString();
        String refresh = com.jayway.jsonpath.JsonPath.read(login, "$.refreshToken");
        String rotated = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
        String next = com.jayway.jsonpath.JsonPath.read(rotated, "$.refreshToken");
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + next + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + next + "\"}"))
                .andExpect(status().isUnauthorized());
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
        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Publico\",\"email\":\"publico@teste.com\",\"senha\":\"123456\",\"tipo\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente publico\",\"email\":\"publico@teste.com\",\"senha\":\"123456\",\"tipo\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("CLIENTE"))
                .andExpect(jsonPath("$.senha").doesNotExist());
        mockMvc.perform(put("/api/configuracoes/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"remetente@gmail.com\",\"senhaAplicativo\":\"abcd efgh ijkl mnop\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAnExistingApplicationSessionToLinkGoogle() throws Exception {
        mockMvc.perform(post("/api/auth/google/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"google-id-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAdministratorRoleToConfigureEmail() throws Exception {
        Usuario cliente = criarUsuario("cliente-email@teste.com", TipoUsuario.CLIENTE);
        String tokenCliente = "Bearer " + jwtService.generate(cliente);
        for (String path : java.util.List.of("/api/usuarios", "/api/consorcios", "/api/grupos/pagina", "/api/cotas")) {
            mockMvc.perform(get(path).header("Authorization", tokenCliente)).andExpect(status().isForbidden());
        }
        Usuario funcionario = criarUsuario("funcionario-permissoes@teste.com", TipoUsuario.FUNCIONARIO);
        String tokenFuncionario = "Bearer " + jwtService.generate(funcionario);
        mockMvc.perform(get("/api/consorcios/pagina").header("Authorization", tokenFuncionario)).andExpect(status().isOk());
        mockMvc.perform(get("/api/clientes/pagina").header("Authorization", tokenFuncionario)).andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/context").header("Authorization", tokenFuncionario)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/consorcios").header("Authorization", tokenFuncionario)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Negado\",\"tipoId\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/configuracoes/email")
            .header("Authorization", "Bearer " + jwtService.generate(cliente))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"remetente@gmail.com\",\"senhaAplicativo\":\"abcd efgh ijkl mnop\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReceiveContemplationInQueueAndNotifyOnlyOnRequest() throws Exception {
        Usuario admin = criarUsuario("admin-fila@teste.com", TipoUsuario.ADMINISTRADOR);

        TipoConsorcio tipo = tipoConsorcioRepository.save(new TipoConsorcio("Veiculos fila"));

        Consorcio consorcio = consorcioRepository.save(new Consorcio("Consorcio fila", tipo));

        Grupo grupo = grupoRepository.save(new Grupo("GRUPO-FILA", consorcio, null, null));

        Cota cota = cotaRepository.save(new Cota("COTA-FILA", grupo, new java.math.BigDecimal("50000.00")));

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
        Usuario clientePortal = usuarioRepository.findByEmail("maria-fila@teste.com").orElseThrow();
        String portalToken = "Bearer " + jwtService.generate(clientePortal);
        mockMvc.perform(get("/api/portal/processos").header("Authorization", portalToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(processoId));
        mockMvc.perform(get("/api/portal/processos/" + processoId).header("Authorization", portalToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cota").value("COTA-FILA"));
        Usuario outro = criarUsuario("outro-cliente-portal@teste.com", TipoUsuario.CLIENTE);
        mockMvc.perform(get("/api/portal/processos/" + processoId).header("Authorization", "Bearer " + jwtService.generate(outro)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/integracoes/comunicacoes/fila/pagina?page=0&size=1&q=CONT-FILA&status=PENDENTE")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].processoId").value(processoId));
        mockMvc.perform(get("/api/integracoes/comunicacoes/fila/pagina?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/integracoes/comunicacoes/fila")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processoId").value(processoId))
                .andExpect(jsonPath("$[0].idExterno").value("CONT-FILA-001"))
                .andExpect(jsonPath("$[0].comunicacao.statusGeral").value("PENDENTE"));

        String history = "/api/integracoes/processos/" + processoId + "/comunicacoes/historico";
        mockMvc.perform(get(history).header("Authorization", portalToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(history).param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(post("/api/integracoes/processos/{processoId}/comunicacao/reenvio", processoId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processoId").value(processoId))
                .andExpect(jsonPath("$.statusGeral").value("PENDENTE"));
        mockMvc.perform(get(history).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));

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
