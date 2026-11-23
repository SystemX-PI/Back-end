package com.systemX.senai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.config.JwtService;
import com.systemX.senai.entity.*;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.enums.TipoContemplacao;
import com.systemX.senai.enums.TipoUsuario;

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
