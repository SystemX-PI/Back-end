package com.systemX.senai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.config.JwtService;
import com.systemX.senai.entity.*;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.enums.TipoContemplacao;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.service.AuditoriaService;
import com.systemX.senai.service.EmailService;
import com.systemX.senai.service.PasswordResetRateLimiter;
import com.systemX.senai.service.WhatsAppService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.servlet.ServletException;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProcessoOperacaoIntegrationTest {
    private static final String PROCESSOS = "/api/processos-contemplacao";
    private static final String BAIXA = "/api/integracoes/processos/{id}/baixa";

    private static final String ENTRADA = "/api/integracoes/contemplacoes";
    @Value("${app.integration.api-key}") String chaveIntegracao;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transactions;
    @MockitoSpyBean AuditoriaService auditoria;
    @MockitoSpyBean WhatsAppService whatsapp;
    @MockitoSpyBean EmailService emailService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Value("${app.jwt.secret}") String segredoTeste;

    @DynamicPropertySource
    static void bancoDeTeste(DynamicPropertyRegistry registry) {
        String port = System.getProperty("systemx.test.mysql.port");
        if (port == null) {
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:processo-operacao;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return;
        }
        int mysqlPort = Integer.parseInt(port);
        if (mysqlPort < 1024 || mysqlPort > 65535) throw new IllegalArgumentException("Porta de teste inválida");
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://127.0.0.1:" + mysqlPort + "/systemx_lote03_test?allowPublicKeyRetrieval=true&sslMode=DISABLED");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "systemx_lote03_test");
        registry.add("spring.datasource.password", () -> System.getenv("SYSTEMX_LOTE03_MYSQL_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Test
    void statusRegistraOperadorDoJwtMesmoComIdLegadoNoCorpo() throws Exception {
        var operador = usuario(TipoUsuario.FUNCIONARIO, "operador-status");
        var outro = usuario(TipoUsuario.ADMINISTRADOR, "outro-status");
        var processo = processo("status", StatusProcesso.EM_ANALISE);
        mvc.perform(patch(PROCESSOS + "/{id}/status", processo.getId())
                .header("Authorization", token(operador)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"EM_PROCESSO_DE_BAIXA","observacao":"Conferido","usuarioId":%d}
                    """.formatted(outro.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responsavelId").value(operador.getId()));
        em.flush();
        var historico = em.createQuery(
                "select h from HistoricoProcesso h where h.processo.id = :id", HistoricoProcesso.class)
                .setParameter("id", processo.getId()).getSingleResult();
        assertEquals(operador.getId(), historico.getUsuario().getId());
        conferirAuditoria(processo, operador, "Alteração de status");
    }

    @Test
    void baixaExigeSessaoOperacionalEUsaOperadorDoJwt() throws Exception {
        var operador = usuario(TipoUsuario.ADMINISTRADOR, "operador-baixa");
        var outro = usuario(TipoUsuario.CLIENTE, "outro-baixa");
        var processo = processo("baixa", StatusProcesso.EM_PROCESSO_DE_BAIXA);
        var body = """
                {"observacao":"Conferido","usuarioId":%d}
                """.formatted(outro.getId());
        mvc.perform(post(BAIXA, processo.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BAIXA, processo.getId()).header("Authorization", token(outro))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(BAIXA, processo.getId()).header("Authorization", token(operador))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(operador.getId()));
        assertEquals(operador.getId(), processo.getResponsavel().getId());
        conferirAuditoria(processo, operador, "Baixa concluída");
    }

    @Test
    void paginaFiltraPorFaseETermoComTotalEOrdemEstavel() throws Exception {
        var operador = usuario(TipoUsuario.FUNCIONARIO, "operador-pagina");
        var cliente = usuario(TipoUsuario.CLIENTE, "cliente-pagina");
        var primeira = processo("lote-pagina-1", StatusProcesso.EM_PROCESSO_DE_BAIXA);
        var segunda = processo("lote-pagina-2", StatusProcesso.EM_PROCESSO_DE_BAIXA);
        var finalizada = processo("lote-pagina-3", StatusProcesso.FINALIZADO);
        var rota = PROCESSOS + "/pagina";
        mvc.perform(get(rota)).andExpect(status().isUnauthorized());
        mvc.perform(get(rota).header("Authorization", token(cliente))).andExpect(status().isForbidden());
        mvc.perform(get(rota).header("Authorization", token(operador))
                .param("q", "LOTE-PAGINA").param("status", "EM_PROCESSO_DE_BAIXA").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value(segunda.getId()));
        mvc.perform(get(rota).header("Authorization", token(operador))
                .param("q", "lote-pagina").param("status", "EM_PROCESSO_DE_BAIXA").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value(primeira.getId()));
        mvc.perform(get(rota).header("Authorization", token(operador))
                .param("q", finalizada.getId().toString()).param("status", "FINALIZADO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(finalizada.getId()));
        mvc.perform(get(rota).header("Authorization", token(operador)).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void baixaEmFaseInelegivelRetornaConflitoSemGravar() throws Exception {
        var operador = usuario(TipoUsuario.FUNCIONARIO, "baixa-inelegivel");
        for (var fase : new StatusProcesso[] { StatusProcesso.EM_ANALISE, StatusProcesso.CANCELADO,
                StatusProcesso.FINALIZADO }) {
            var processo = processo("baixa-" + fase, fase);
            mvc.perform(post(BAIXA, processo.getId()).header("Authorization", token(operador))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\":\"Conferido\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(get(BAIXA, processo.getId()).header("Authorization", token(operador)))
                    .andExpect(status().isNotFound());
            assertEquals(fase, processo.getStatus());
        }
    }

    @Test
    void segundaBaixaRetornaOComprovanteOriginal() throws Exception {
        var primeiro = usuario(TipoUsuario.FUNCIONARIO, "primeira-baixa");
        var segundo = usuario(TipoUsuario.ADMINISTRADOR, "segunda-baixa");
        var processo = processo("baixa-repetida", StatusProcesso.EM_PROCESSO_DE_BAIXA);
        var original = mvc.perform(post(BAIXA, processo.getId()).header("Authorization", token(primeiro))
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\":\"Original\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(primeiro.getId()))
                .andReturn().getResponse().getContentAsString();
        var repetida = mvc.perform(post(BAIXA, processo.getId()).header("Authorization", token(segundo))
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\":\"Segunda tentativa\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(original, repetida);
        assertEquals(StatusProcesso.FINALIZADO, processo.getStatus());
        conferirAuditoria(processo, primeiro, "Baixa concluída");
    }

    @Test
    void transicaoRejeitaSaltosRegressaoFinalizacaoDiretaEReabertura() throws Exception {
        var operador = usuario(TipoUsuario.FUNCIONARIO, "transicao");
        var emAnalise = processo("em-analise", StatusProcesso.EM_ANALISE);
        mvc.perform(get(PROCESSOS + "/{id}", emAnalise.getId()).header("Authorization", token(operador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transicoesPermitidas[0]").value("EM_PROCESSO_DE_BAIXA"))
                .andExpect(jsonPath("$.transicoesPermitidas[1]").value("CANCELADO"));
        negarTransicao(operador, emAnalise, StatusProcesso.DOCUMENTOS_RECEBIDOS);
        negarTransicao(operador, emAnalise, StatusProcesso.FINALIZADO);
        negarTransicao(operador, processo("salto", StatusProcesso.CONTEMPLADO), StatusProcesso.EM_ANALISE);
        negarTransicao(operador, processo("encerrado", StatusProcesso.FINALIZADO), StatusProcesso.EM_ANALISE);

        mvc.perform(patch(PROCESSOS + "/{id}/status", emAnalise.getId())
                .header("Authorization", token(operador)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transicoesPermitidas").isEmpty());
        negarTransicao(operador, emAnalise, StatusProcesso.EM_PROCESSO_DE_BAIXA);
    }

    @Test
    void cancelamentoEstaDisponivelApenasParaOrigensAtivas() throws Exception {
        var operador = usuario(TipoUsuario.FUNCIONARIO, "cancelamento");
        for (var origem : StatusProcesso.values()) {
            var processo = processo("cancelar-" + origem, origem);
            if (origem == StatusProcesso.FINALIZADO || origem == StatusProcesso.CANCELADO) {
                negarTransicao(operador, processo, StatusProcesso.CANCELADO);
            } else {
                mvc.perform(patch(PROCESSOS + "/{id}/status", processo.getId())
                        .header("Authorization", token(operador)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\"}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADO"));
            }
        }
    }

    @Test
    void comprovanteExigePerfilEContaEPortalMostraConclusaoAoTitular() throws Exception {
        var conta = conta("A");
        var outraConta = conta("B");
        var titular = usuario(TipoUsuario.CLIENTE, "titular-baixa");
        titular.setContaId(conta.getId());
        var operador = usuario(TipoUsuario.FUNCIONARIO, "operador-conta-a");
        operador.setContaId(conta.getId());
        var externo = usuario(TipoUsuario.FUNCIONARIO, "operador-conta-b");
        externo.setContaId(outraConta.getId());
        var processo = processo("conta-baixa", StatusProcesso.EM_PROCESSO_DE_BAIXA);
        processo.setCliente(titular);
        em.flush();
        var id = processo.getId();

        solicitarBaixa(id, token(externo), "indevida").andExpect(status().isNotFound());
        var original = solicitarBaixa(id, token(operador), "Observação original")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        solicitarBaixa(id, token(externo), "indevida").andExpect(status().isNotFound());
        mvc.perform(get(BAIXA, id).header("Authorization", token(externo))).andExpect(status().isNotFound());
        solicitarBaixa(id, token(titular), "indevida").andExpect(status().isForbidden());
        mvc.perform(get(BAIXA, id).header("Authorization", token(titular))).andExpect(status().isForbidden());
        mvc.perform(get(BAIXA, id)).andExpect(status().isUnauthorized());
        var consulta = mvc.perform(get(BAIXA, id).header("Authorization", token(operador)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(original, consulta);
        mvc.perform(get("/api/portal/processos/{id}", id).header("Authorization", token(titular)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historico[0].statusAnterior").value("EM_PROCESSO_DE_BAIXA"))
                .andExpect(jsonPath("$.historico[0].novoStatus").value("FINALIZADO"))
                .andExpect(jsonPath("$.historico[0].observacao").value("Observação original"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void baixasSimultaneasRetornamMesmoComprovanteEGravamUmaVez() throws Exception {
        var fixture = criarFixture("concorrente");
        var encontro = new CyclicBarrier(2);
        doAnswer(invocation -> {
            var operador = invocation.callRealMethod();
            encontro.await(10, TimeUnit.SECONDS);
            return operador;
        }).when(auditoria).exigirOperador();

        var executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> solicitarBaixa(fixture.id(), fixture.primeiroToken(), "Primeira")
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var segunda = executor.submit(() -> solicitarBaixa(fixture.id(), fixture.segundoToken(), "Segunda")
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(primeira.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        transactions.executeWithoutResult(status -> {
            Long id = fixture.id();
            assertEquals(1L, contarEventos("Baixa", "processo.id", id));
            assertEquals(1L, contarEventos("HistoricoProcesso", "processo.id", id));
            assertEquals(1L, contarEventos("Auditoria", "registroId", id));
            var baixa = em.createQuery("select b from Baixa b where b.processo.id = :id", Baixa.class)
                    .setParameter("id", id).getSingleResult();
            var historico = em.createQuery("select h from HistoricoProcesso h where h.processo.id = :id", HistoricoProcesso.class)
                    .setParameter("id", id).getSingleResult();
            assertEquals(StatusProcesso.EM_PROCESSO_DE_BAIXA, historico.getStatusAnterior());
            assertEquals(StatusProcesso.FINALIZADO, historico.getNovoStatus());
            assertEquals(StatusProcesso.FINALIZADO, baixa.getProcesso().getStatus());
            assertEquals(baixa.getUsuario().getId(), historico.getUsuario().getId());
            assertEquals(baixa.getData(), historico.getData());
            assertEquals(baixa.getObservacao(), historico.getObservacao());
            conferirAuditoria(baixa.getProcesso(), baixa.getUsuario(), "Baixa concluída");
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void falhaAposGravacoesReverteBaixaProcessoHistoricoEAuditoria() {
        var fixture = criarFixture("rollback");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            em.flush();
            throw new IllegalStateException("Falha de teste após as gravações");
        }).when(auditoria).registrar("Baixa concluída", "Processo", fixture.id(), "Processo finalizado");

        var erro = assertThrows(ServletException.class,
                () -> solicitarBaixa(fixture.id(), fixture.primeiroToken(), "Reverter"));
        assertTrue(erro.getCause().getMessage().contains("Falha de teste"));
        transactions.executeWithoutResult(status -> {
            Long id = fixture.id();
            assertEquals(0L, contarEventos("Baixa", "processo.id", id));
            assertEquals(0L, contarEventos("HistoricoProcesso", "processo.id", id));
            assertEquals(0L, contarEventos("Auditoria", "registroId", id));
            var processo = em.find(ProcessoContemplacao.class, id);
            assertEquals(StatusProcesso.EM_PROCESSO_DE_BAIXA, processo.getStatus());
            assertNull(processo.getResponsavel());
        });
    }

    private ResultActions solicitarBaixa(Long id, String token, String observacao)
            throws Exception {
        return mvc.perform(post(BAIXA, id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"observacao\":\"%s\"}".formatted(observacao)));
    }

    private record Fixture(Long id, String primeiroToken, String segundoToken) {}

    private Fixture criarFixture(String codigo) {
        String referencia = codigo + "-" + UUID.randomUUID().toString().substring(0, 8);
        return transactions.execute(status -> {
            var primeiro = usuario(TipoUsuario.FUNCIONARIO, referencia + "-primeiro");
            var segundo = usuario(TipoUsuario.ADMINISTRADOR, referencia + "-segundo");
            var processo = processo(referencia, StatusProcesso.EM_PROCESSO_DE_BAIXA);
            return new Fixture(processo.getId(), token(primeiro), token(segundo));
        });
    }

    private PlatformAccount conta(String nome) {
        var conta = new PlatformAccount();
        conta.setNome(nome);
        conta.setBrandName(nome);
        em.persist(conta);
        return conta;
    }

    @Test
    void isolamentoAplicaEscopoNasListasNosIdsENasConfiguracoes() throws Exception {
        var casaA = conta("Isolamento A");
        var casaB = conta("Isolamento B");
        var operadorA = usuario(TipoUsuario.FUNCIONARIO, "escopo-op-a");
        operadorA.setContaId(casaA.getId());
        var operadorB = usuario(TipoUsuario.FUNCIONARIO, "escopo-op-b");
        operadorB.setContaId(casaB.getId());
        var clienteA = usuario(TipoUsuario.CLIENTE, "escopo-cliente-a");
        clienteA.setContaId(casaA.getId());
        var membro = usuario(TipoUsuario.USUARIO, "escopo-membro-a");
        membro.setContaId(casaA.getId());
        var clienteB = usuario(TipoUsuario.CLIENTE, "escopo-cliente-b");
        clienteB.setContaId(casaB.getId());
        var admin = usuario(TipoUsuario.ADMINISTRADOR, "escopo-admin");
        var a = processo("escopo-a", StatusProcesso.EM_ANALISE);
        a.setCliente(clienteA);
        var b = processo("escopo-b", StatusProcesso.EM_ANALISE);
        b.setCliente(clienteB);
        notificar(a, operadorA);
        notificar(b, operadorB);
        em.flush();

        mvc.perform(get(PROCESSOS + "/pagina").param("q", "escopo-").header("Authorization", token(operadorA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(a.getId()));
        mvc.perform(get("/api/integracoes/comunicacoes/fila/pagina").header("Authorization", token(operadorA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/integracoes/comunicacoes/fila/resumo").header("Authorization", token(operadorA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/integracoes/comunicacoes/historico").param("inicio", "2026-11-23")
                .param("fim", "2026-11-23").header("Authorization", token(operadorA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].processId").value(a.getId()));
        mvc.perform(get(PROCESSOS + "/{id}", b.getId()).header("Authorization", token(admin)))
                .andExpect(status().isOk());
        mvc.perform(get(PROCESSOS + "/{id}", b.getId()).header("Authorization", token(operadorA)))
                .andExpect(status().isNotFound());
        for (String recurso : new String[] {"comunicacao", "comunicacoes/historico", "baixa"}) {
            mvc.perform(get("/api/integracoes/processos/{id}/" + recurso, b.getId())
                    .header("Authorization", token(operadorA))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/integracoes/processos/{id}/comunicacoes/historico", a.getId())
                .header("Authorization", token(operadorA))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/integracoes/processos/{id}/comunicacao/reenvio", b.getId())
                .header("Authorization", token(operadorA))).andExpect(status().isNotFound());
        mvc.perform(post("/api/integracoes/processos/{id}/comunicacao/reenvio", a.getId())
                .header("Authorization", token(operadorA))).andExpect(status().isForbidden());
        mvc.perform(patch(PROCESSOS + "/{id}/status", a.getId()).header("Authorization", token(operadorB))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isNotFound());
        assertEquals(StatusProcesso.EM_ANALISE, a.getStatus());
        mvc.perform(get("/api/auditoria/pagina").header("Authorization", token(operadorA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria/pagina").header("Authorization", token(admin))).andExpect(status().isOk());

        mvc.perform(get("/api/platform/context").header("Authorization", token(membro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.id").value(casaA.getId()));
        mvc.perform(get("/api/platform/context").param("accountId", casaB.getId().toString())
                .header("Authorization", token(membro))).andExpect(status().isNotFound());
        var marca = "{\"name\":\"Alterada\",\"primary\":\"#315fe8\",\"accent\":\"#35cda0\"}";
        mvc.perform(put("/api/platform/brand").header("Authorization", token(membro))
                .contentType(MediaType.APPLICATION_JSON).content(marca)).andExpect(status().isForbidden());
        mvc.perform(put("/api/platform/brand").param("accountId", casaB.getId().toString())
                .header("Authorization", token(clienteA)).contentType(MediaType.APPLICATION_JSON).content(marca))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/platform/brand").header("Authorization", token(clienteA))
                .contentType(MediaType.APPLICATION_JSON).content(marca)).andExpect(status().isNoContent());
        mvc.perform(get("/api/portal/processos/{id}", a.getId()).header("Authorization", token(clienteA)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/portal/processos/{id}", a.getId()).header("Authorization", token(clienteB)))
                .andExpect(status().isNotFound());

        String revogado = token(operadorA);
        operadorA.invalidarSessoes();
        em.flush();
        mvc.perform(get(PROCESSOS + "/{id}", a.getId()).header("Authorization", revogado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void entradaValidaCredencialCorpoReferenciasEReentrega() throws Exception {
        var base = processo("entrada-validacao", StatusProcesso.CONTEMPLADO).getContemplacao();
        long cota = base.getCota().getId();
        long assembleia = base.getAssembleia().getId();
        var outroGrupo = processo("outro-grupo", StatusProcesso.CONTEMPLADO).getContemplacao();
        String evento = payloadEntrada("entrada-http", "entrada-http@example.test", cota, assembleia, "500");
        mvc.perform(post(ENTRADA).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden());
        var usuarioConta = usuario(TipoUsuario.CLIENTE, "sem-chave-parceira");
        mvc.perform(post(ENTRADA).header("Authorization", token(usuarioConta))
                .contentType(MediaType.APPLICATION_JSON).content(evento)).andExpect(status().isForbidden());
        mvc.perform(post(ENTRADA).header("X-Integration-Key", "invalida")
                .contentType(MediaType.APPLICATION_JSON).content(evento)).andExpect(status().isForbidden());
        enviarEntrada("{").andExpect(status().isBadRequest());
        enviarEntrada("{}").andExpect(status().isBadRequest());
        enviarEntrada(payloadEntrada("cota-ausente", "entrada-http@example.test", Long.MAX_VALUE, assembleia, "500"))
                .andExpect(status().isNotFound());
        enviarEntrada(payloadEntrada("assembleia-ausente", "entrada-http@example.test", cota, Long.MAX_VALUE, "500"))
                .andExpect(status().isNotFound());
        enviarEntrada(payloadEntrada("grupos-divergentes", "entrada-http@example.test", cota,
                outroGrupo.getAssembleia().getId(), "500")).andExpect(status().isBadRequest());

        String primeira = enviarEntrada(evento).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(primeira, "$.processoId");
        enviarEntrada(evento).andExpect(status().isCreated())
                .andExpect(jsonPath("$.processoId").value(id.longValue()));
        enviarEntrada(payloadEntrada("entrada-http", "outra-conta@example.test", cota, assembleia, "500"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.processoId").doesNotExist());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void entradaSimultaneaGravaUmaVezEErroDePersistenciaReverte() throws Exception {
        var referencia = UUID.randomUUID().toString();
        var ids = transactions.execute(status -> {
            var base = processo("base-" + referencia, StatusProcesso.CONTEMPLADO).getContemplacao();
            return new long[] {base.getCota().getId(), base.getAssembleia().getId()};
        });
        String email = referencia + "@example.test";
        String evento = payloadEntrada(referencia, email, ids[0], ids[1], "500");
        var encontro = new CyclicBarrier(2);
        java.util.concurrent.Callable<Number> solicitar = () -> {
            encontro.await(10, TimeUnit.SECONDS);
            var resposta = enviarEntrada(evento).andExpect(status().isCreated()).andReturn().getResponse();
            return com.jayway.jsonpath.JsonPath.read(resposta.getContentAsString(), "$.processoId");
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(solicitar);
            var b = executor.submit(solicitar);
            assertEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        String emailFalha = "falha-" + email;
        enviarEntrada(payloadEntrada("falha-" + referencia, emailFalha, ids[0], ids[1], "99999999999999999999"))
                .andExpect(status().isConflict());
        transactions.executeWithoutResult(status -> {
            assertEquals(1L, em.createQuery("select count(p) from ProcessoContemplacao p where p.idExterno = :id", Long.class)
                    .setParameter("id", referencia).getSingleResult());
            assertEquals(1L, em.createQuery("select count(c) from Comunicacao c where c.processo.idExterno = :id", Long.class)
                    .setParameter("id", referencia).getSingleResult());
            assertEquals(0L, em.createQuery("select count(u) from Usuario u where u.email = :email", Long.class)
                    .setParameter("email", emailFalha).getSingleResult());
        });
    }

    @Test
    void tentativaAceitaOuFalhaFicaNoHistoricoSemDuplicarReenvio() throws Exception {
        var operador = usuario(TipoUsuario.FUNCIONARIO, "notificador");
        var processo = processo("historico-tentativa", StatusProcesso.CLIENTE_CONTATADO);
        notificar(processo, operador);
        var original = em.createQuery("select c from Comunicacao c where c.processo.id = :id", Comunicacao.class)
                .setParameter("id", processo.getId()).getSingleResult();
        original.setComunicadoOficial(true);
        original.setAssunto("Aviso de teste");
        original.setMensagem("Mensagem original");
        doReturn(true).when(emailService).isEnabled();
        doThrow(new IllegalStateException("diagnóstico interno fictício"))
                .when(emailService).send(original.getDestinatario(), original.getAssunto(), original.getMensagem());
        String reenvio = "/api/integracoes/processos/{id}/comunicacao/reenvio";
        String chave = UUID.randomUUID().toString();
        String primeira = mvc.perform(post(reenvio, processo.getId()).header("Authorization", token(operador))
                .header("Idempotency-Key", chave)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email.status").value("FALHA"))
                .andExpect(jsonPath("$.email.enviadaEm").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String repetida = mvc.perform(post(reenvio, processo.getId()).header("Authorization", token(operador))
                .header("Idempotency-Key", chave)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(primeira, repetida);
        assertTrue(!primeira.contains("diagnóstico interno"));
        verify(emailService, times(1)).send(original.getDestinatario(), original.getAssunto(), original.getMensagem());
        assertEquals(com.systemX.senai.enums.StatusComunicacao.PENDENTE, original.getStatus());

        String telefone = "5511999999999";
        doReturn(true).when(whatsapp).isEnabled();
        doReturn("id-ficticio-do-provedor").when(whatsapp).send(telefone, "Aviso");
        var request = post("/api/integracoes/processos/{id}/comunicacao/whatsapp", processo.getId())
                .header("Authorization", token(operador)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefone\":\"" + telefone + "\",\"mensagem\":\"Aviso\"}");
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.whatsapp.status").value("SUCESSO"))
                .andExpect(jsonPath("$.whatsapp.enviadaEm").isNotEmpty());
        mvc.perform(get("/api/integracoes/processos/{id}/comunicacoes/historico", processo.getId())
                .header("Authorization", token(operador))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[1].comunicadoOriginalId").value(original.getId()));
        assertEquals(2L, contarEventos("HistoricoComunicacao", "comunicacao.processo.id", processo.getId()));
        assertEquals("id-ficticio-do-provedor", em.createQuery("""
                select c.provedorMensagemId from Comunicacao c
                where c.processo.id = :id and c.canal = com.systemX.senai.enums.CanalComunicacao.WHATSAPP
                """, String.class).setParameter("id", processo.getId()).getSingleResult());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void limitadorCompartilhaCotaEntreInstanciasEConcorrentes() throws Exception {
        var primeira = new PasswordResetRateLimiter(jdbc, segredoTeste);
        var segunda = new PasswordResetRateLimiter(jdbc, segredoTeste);
        var tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        var origem = "origem-teste-" + UUID.randomUUID();
        var email = UUID.randomUUID() + "@example.test";
        var encontro = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> {
                encontro.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> primeira.request(origem, email));
            });
            var b = pool.submit(() -> {
                encontro.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> segunda.request(origem, email));
            });
            assertEquals(java.util.Set.of(PasswordResetRateLimiter.Decision.ACCEPT,
                    PasswordResetRateLimiter.Decision.ORIGIN_LIMIT),
                    java.util.Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
        } finally {
            pool.shutdownNow();
        }
        var segundaOrigem = origem + "-2";
        assertEquals(PasswordResetRateLimiter.Decision.EMAIL_LIMIT,
                tx.execute(status -> segunda.request(segundaOrigem, email)));
    }

    private ResultActions enviarEntrada(String json) throws Exception {
        return mvc.perform(post(ENTRADA).header("X-Integration-Key", chaveIntegracao)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String payloadEntrada(String externo, String email, long cota, long assembleia, String credito) {
        return """
                {"origem":"parceiro-teste","idExterno":"%s","nome":"Cliente fictício","email":"%s",
                 "cotaId":%d,"assembleiaId":%d,"dataContemplacao":"2026-11-23","tipo":"SORTEIO","valorCredito":%s}
                """.formatted(externo, email, cota, assembleia, credito);
    }

    private void notificar(ProcessoContemplacao processo, Usuario operador) {
        var comunicacao = new Comunicacao();
        comunicacao.setProcesso(processo);
        comunicacao.setResponsavel(operador);
        comunicacao.setDestinatario("notificacao@example.test");
        comunicacao.setCanal(com.systemX.senai.enums.CanalComunicacao.EMAIL);
        comunicacao.setStatus(com.systemX.senai.enums.StatusComunicacao.PENDENTE);
        comunicacao.setCriadaEm(java.time.LocalDateTime.of(2026, 11, 23, 12, 0));
        em.persist(comunicacao);
    }

    private void negarTransicao(Usuario operador, ProcessoContemplacao processo, StatusProcesso destino)
            throws Exception {
        var origem = processo.getStatus();
        long historicoAnterior = contarEventos("HistoricoProcesso", "processo.id", processo.getId());
        long auditoriaAnterior = contarEventos("Auditoria", "registroId", processo.getId());
        mvc.perform(patch(PROCESSOS + "/{id}/status", processo.getId())
                .header("Authorization", token(operador)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(destino)))
                .andExpect(status().isConflict());
        assertEquals(origem, processo.getStatus());
        assertEquals(historicoAnterior, contarEventos("HistoricoProcesso", "processo.id", processo.getId()));
        assertEquals(auditoriaAnterior, contarEventos("Auditoria", "registroId", processo.getId()));
    }

    private long contarEventos(String entidade, String campo, Long id) {
        return em.createQuery("select count(e) from " + entidade + " e where e." + campo + " = :id", Long.class)
                .setParameter("id", id).getSingleResult();
    }

    private void conferirAuditoria(ProcessoContemplacao processo, Usuario operador, String acao) {
        var registro = em.createQuery("""
                select a from Auditoria a where a.registroId = :id and a.acao = :acao
                """, Auditoria.class)
                .setParameter("id", processo.getId()).setParameter("acao", acao).getSingleResult();
        assertEquals(operador.getId(), registro.getUsuario().getId());
    }

    private Usuario usuario(TipoUsuario tipo, String nome) {
        var usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(nome + "@example.test");
        usuario.setSenha("valor-ficticio-sem-login");
        usuario.setTipo(tipo);
        em.persist(usuario);
        return usuario;
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwt.generate(usuario);
    }

    private ProcessoContemplacao processo(String codigo, StatusProcesso status) {
        var tipo = new TipoConsorcio("Tipo " + codigo);
        em.persist(tipo);
        var consorcio = new Consorcio("Consórcio " + codigo, tipo);
        em.persist(consorcio);
        var grupo = new Grupo("Grupo " + codigo, consorcio, 10, BigDecimal.TEN);
        em.persist(grupo);
        var cota = new Cota("Cota " + codigo, grupo, BigDecimal.TEN);
        em.persist(cota);
        var data = LocalDate.of(2026, 11, 23);
        var assembleia = Assembleia.builder().grupo(grupo).data(data).build();
        em.persist(assembleia);
        var contemplacao = Contemplacao.criar(cota, assembleia, data, TipoContemplacao.SORTEIO, BigDecimal.TEN);
        em.persist(contemplacao);
        var processo = ProcessoContemplacao.builder()
                .contemplacao(contemplacao).status(status).idExterno(codigo).build();
        em.persist(processo);
        return processo;
    }
}
