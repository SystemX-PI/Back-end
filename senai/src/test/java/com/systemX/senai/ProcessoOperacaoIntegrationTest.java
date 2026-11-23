package com.systemX.senai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import jakarta.servlet.ServletException;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProcessoOperacaoIntegrationTest {
    private static final String PROCESSOS = "/api/processos-contemplacao";
    private static final String BAIXA = "/api/integracoes/processos/{id}/baixa";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transactions;
    @MockitoSpyBean AuditoriaService auditoria;

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
