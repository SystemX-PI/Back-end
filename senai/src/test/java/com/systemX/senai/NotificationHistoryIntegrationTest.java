package com.systemX.senai;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.systemX.senai.config.JwtService;
import com.systemX.senai.entity.Assembleia;
import com.systemX.senai.entity.Comunicacao;
import com.systemX.senai.entity.Consorcio;
import com.systemX.senai.entity.Contemplacao;
import com.systemX.senai.entity.Cota;
import com.systemX.senai.entity.Grupo;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.entity.TipoConsorcio;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.CanalComunicacao;
import com.systemX.senai.enums.StatusComunicacao;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.enums.TipoContemplacao;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.UsuarioRepository;
import com.systemX.senai.service.PlatformService;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.systemX.senai.NotificationHistoryIntegrationTest$SqlCapture"
})
@AutoConfigureMockMvc
@Transactional
class NotificationHistoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlatformService platform;
    @Autowired UsuarioRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;

    public static class SqlCapture implements StatementInspector {
        static final ConcurrentLinkedQueue<String> queries = new ConcurrentLinkedQueue<>();
        @Override public String inspect(String sql) {
            queries.add(sql.toLowerCase(Locale.ROOT));
            return sql;
        }
    }

    @Test void countsAllMatchesButLimitsRowsInSqlAndProtectsProviderDiagnostics() throws Exception {
        var a = owner("summary-a@test.com");
        var b = owner("summary-b@test.com");
        var process = process(a, a, "own");
        for (int i = 0; i < 25; i++) {
            StatusComunicacao status = i < 10 ? StatusComunicacao.SUCESSO
                    : i < 15 ? StatusComunicacao.REALIZADA
                    : i < 20 ? StatusComunicacao.FALHA
                    : i == 24 ? StatusComunicacao.SEM_RESPOSTA : StatusComunicacao.PENDENTE;
            communication(process, a, i % 2 == 0 ? CanalComunicacao.EMAIL : CanalComunicacao.WHATSAPP,
                    status, LocalDateTime.of(2026, 11, 5, 12, 0).plusMinutes(i));
        }
        communication(process(b, b, "other"), b, CanalComunicacao.EMAIL,
                StatusComunicacao.SUCESSO, LocalDateTime.of(2026, 11, 5, 13, 0));
        communication(process, a, CanalComunicacao.EMAIL, StatusComunicacao.SUCESSO,
                LocalDateTime.of(2025, 11, 5, 12, 0));
        em.flush();
        em.clear();
        SqlCapture.queries.clear();
        String body = mvc.perform(get("/api/platform/notification-history")
                    .param("start", "2026-11-01").param("end", "2026-11-05")
                    .header("Authorization", token(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.accepted").value(15))
                .andExpect(jsonPath("$.failed").value(5)).andExpect(jsonPath("$.pending").value(5))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].externalId").value("own"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("internal-provider-diagnostic"));
        var member = new Usuario(); member.setNome("History team"); member.setEmail("history-team@test.com");
        member.setSenha(encoder.encode("test-password-only")); member.setTipo(TipoUsuario.USUARIO);
        member.setContaId(a.getContaId()); users.saveAndFlush(member);
        String memberBody = mvc.perform(get("/api/platform/notification-history")
                    .param("start", "2026-11-01").param("end", "2026-11-05")
                    .header("Authorization", token(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(25))
                .andReturn().getResponse().getContentAsString();
        assertFalse(memberBody.contains("internal-provider-diagnostic"));
        var recentSql = SqlCapture.queries.stream()
                .filter(sql -> sql.contains("from comunicacao") && sql.contains("order by"))
                .findFirst().orElseThrow();
        assertTrue(recentSql.contains("fetch first") || recentSql.contains("limit"), recentSql);
        mvc.perform(get("/api/platform/notification-history")
                    .param("start", "2026-11-01").param("end", "2026-11-05").param("channel", "EMAIL")
                    .header("Authorization", token(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(13))
                .andExpect(jsonPath("$.accepted").value(8)).andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.pending").value(3));
        mvc.perform(get("/api/platform/notification-history")
                    .param("accountId", String.valueOf(b.getContaId()))
                    .param("start", "2026-11-01").param("end", "2026-11-05")
                    .header("Authorization", token(a)))
                .andExpect(status().isNotFound());
    }

    @Test void globalViewIncludesLegacyProcessWithoutTenantAndMemberCannotUseGlobalRoute() throws Exception {
        var owner = owner("global-owner@test.com");
        var admin = new Usuario();
        admin.setNome("History admin"); admin.setEmail("history-admin@test.com");
        admin.setSenha(encoder.encode("test-password-only")); admin.setTipo(TipoUsuario.ADMINISTRADOR);
        users.saveAndFlush(admin);
        communication(process(null, admin, "legacy"), admin, CanalComunicacao.EMAIL,
                StatusComunicacao.SUCESSO, LocalDateTime.of(2026, 11, 5, 23, 59, 59));
        em.flush();
        mvc.perform(get("/api/integracoes/comunicacoes/historico")
                    .param("inicio", "2026-11-05").param("fim", "2026-11-05")
                    .header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("legacy"));
        mvc.perform(get("/api/integracoes/comunicacoes/historico")
                    .param("inicio", "2026-11-05").param("fim", "2026-11-05")
                    .header("Authorization", token(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/notification-history")
                    .param("start", "2026-11-05").param("end", "2026-11-05"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/notification-history")
                    .param("start", "2026-11-01").param("end", "2026-11-05")
                    .header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void rejectsInvalidDatesAndCountsInclusivePeriod() throws Exception {
        var owner = owner("date-owner@test.com");
        for (String[] dates : new String[][] {
                {"2026-11-05", "2026-11-01"}, {"2026-01-01", "2027-01-02"},
                {"9999-12-31", "9999-12-31"}, {"2026-11-01", "+999999999-12-31"}}) {
            mvc.perform(get("/api/platform/notification-history")
                        .param("start", dates[0]).param("end", dates[1])
                        .header("Authorization", token(owner))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/platform/notification-history")
                    .param("start", "2026-01-01").param("end", "2027-01-01")
                    .header("Authorization", token(owner))).andExpect(status().isOk());
    }

    private Usuario owner(String email) {
        return users.findById(platform.signup("History account", email, "test-password-only").id()).orElseThrow();
    }
    private String token(Usuario user) { return "Bearer " + jwt.generate(user); }

    private ProcessoContemplacao process(Usuario client, Usuario actor, String externalId) {
        var type = new TipoConsorcio("Type " + externalId); em.persist(type);
        var consortium = new Consorcio("Consortium " + externalId, type); em.persist(consortium);
        var group = new Grupo("Group " + externalId, consortium, 10, BigDecimal.TEN); em.persist(group);
        var quota = new Cota("Quota " + externalId, group, BigDecimal.TEN); em.persist(quota);
        var assembly = Assembleia.builder().grupo(group).data(LocalDate.of(2026, 11, 1)).build(); em.persist(assembly);
        var award = Contemplacao.criar(quota, assembly, LocalDate.of(2026, 11, 1),
                TipoContemplacao.SORTEIO, BigDecimal.TEN); em.persist(award);
        var process = new ProcessoContemplacao(); process.setCliente(client); process.setResponsavel(actor);
        process.setContemplacao(award); process.setStatus(StatusProcesso.AGUARDANDO_COMUNICACAO);
        process.setIdExterno(externalId); em.persist(process);
        return process;
    }
    private void communication(ProcessoContemplacao process, Usuario actor, CanalComunicacao channel,
            StatusComunicacao status, LocalDateTime date) {
        var communication = new Comunicacao(); communication.setProcesso(process);
        communication.setResponsavel(actor); communication.setCanal(channel); communication.setStatus(status);
        communication.setDestinatario("recipient@example.test"); communication.setCriadaEm(date);
        if (status == StatusComunicacao.SUCESSO || status == StatusComunicacao.REALIZADA) {
            communication.setEnviadaEm(date);
        } else {
            communication.setErro("internal-provider-diagnostic should not reach the browser");
        }
        em.persist(communication);
    }
}
