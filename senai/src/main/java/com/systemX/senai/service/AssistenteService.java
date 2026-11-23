package com.systemX.senai.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systemX.senai.dto.AssistenteAcao;
import com.systemX.senai.dto.AssistenteResposta;
import com.systemX.senai.dto.ComunicacaoResponse;
import com.systemX.senai.dto.WhatsAppRequest;
import com.systemX.senai.entity.Auditoria;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.repository.AuditoriaRepository;
import com.systemX.senai.repository.UsuarioRepository;

@Service
public class AssistenteService {
    private static final Duration PROPOSAL_TTL = Duration.ofMinutes(5);
    private final ObjectMapper json;
    private final UsuarioRepository usuarios;
    private final AuditoriaRepository auditorias;
    private final ProcessoContemplacaoService processos;
    private final IntegracaoContempladoService comunicacoes;
    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, Proposal> proposals = new ConcurrentHashMap<>();

    public AssistenteService(ObjectMapper json, UsuarioRepository usuarios, AuditoriaRepository auditorias,
            ProcessoContemplacaoService processos, IntegracaoContempladoService comunicacoes,
            @Value("${app.assistant.groq.enabled:false}") boolean enabled,
            @Value("${app.assistant.groq.api-key:}") String apiKey,
            @Value("${app.assistant.groq.model:llama-3.3-70b-versatile}") String model) {
        this.json = json; this.usuarios = usuarios; this.auditorias = auditorias;
        this.processos = processos; this.comunicacoes = comunicacoes;
        this.enabled = enabled; this.apiKey = apiKey; this.model = model;
    }

    public AssistenteResposta conversar(String mensagem) {
        Usuario usuario = usuarioAtual();
        if (!enabled || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assistente indisponível. Configure GROQ_ENABLED e GROQ_API_KEY no servidor.");
        }
        try {
            JsonNode resposta = chamarGroq(mensagem, usuario);
            JsonNode chamada = resposta.path("choices").path(0).path("message").path("tool_calls");
            if (chamada.isArray() && !chamada.isEmpty()) return tratarFerramenta(chamada.get(0), usuario);
            String texto = resposta.path("choices").path(0).path("message").path("content").asText();
            return new AssistenteResposta(texto.isBlank() ? "Não consegui interpretar o pedido." : texto, null);
        } catch (ResponseStatusException | com.systemX.senai.exception.ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar o assistente agora.");
        }
    }

    @Transactional
    public ComunicacaoResponse confirmarNotificacao(String token) {
        Usuario usuario = usuarioAtual();
        Proposal proposal = proposals.get(token);
        if (proposal == null || !proposal.userId().equals(usuario.getId())
                || proposal.expiresAt().isBefore(Instant.now()) || !proposals.remove(token, proposal)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Esta proposta expirou ou não pertence à sua sessão.");
        }
        ComunicacaoResponse resposta = comunicacoes.enviarWhatsApp(proposal.processoId(),
                new WhatsAppRequest(proposal.telefone(), proposal.mensagem()), UUID.fromString(token));
        auditorias.save(Auditoria.builder().usuario(usuario).acao("ASSISTENTE_NOTIFICACAO_CONFIRMADA")
                .entidade("PROCESSO_CONTEMPLACAO").registroId(proposal.processoId())
                .detalhes("Notificação WhatsApp confirmada pelo usuário via assistente.").build());
        return resposta;
    }

    private AssistenteResposta tratarFerramenta(JsonNode chamada, Usuario usuario) throws Exception {
        String nome = chamada.path("function").path("name").asText();
        JsonNode argumentos = json.readTree(chamada.path("function").path("arguments").asText("{}"));
        if ("consultar_processo".equals(nome)) {
            long id = argumentos.path("processoId").asLong(0);
            if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um processo válido.");
            var processo = processos.buscar(id);
            return new AssistenteResposta("Processo #" + processo.id() + ": status " + processo.status()
                    + ", atualizado em " + processo.atualizadoEm() + ".", null);
        }
        if ("propor_notificacao_whatsapp".equals(nome)) {
            long id = argumentos.path("processoId").asLong(0);
            String telefone = argumentos.path("telefone").asText("").replaceAll("\\D", "");
            String mensagem = argumentos.path("mensagem").asText("").trim();
            if (id <= 0 || telefone.length() < 10 || telefone.length() > 15 || mensagem.isBlank() || mensagem.length() > 1000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A proposta precisa de processo, telefone e mensagem válidos.");
            }
            processos.buscar(id);
            if (usuario.getTipo() != com.systemX.senai.enums.TipoUsuario.ADMINISTRADOR && usuario.getContaId() != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Envio disponível apenas para a operação interna.");
            }
            String token = UUID.randomUUID().toString();
            proposals.put(token, new Proposal(usuario.getId(), id, telefone, mensagem, Instant.now().plus(PROPOSAL_TTL)));
            AssistenteAcao acao = new AssistenteAcao(token, "NOTIFICAR_WHATSAPP", id, telefone, mensagem,
                    "Revise os dados. O envio só ocorrerá após sua confirmação.");
            return new AssistenteResposta("Preparei uma notificação. Revise e confirme para enviar.", acao);
        }
        return new AssistenteResposta("Essa ação não é permitida pelo assistente.", null);
    }

    private JsonNode chamarGroq(String mensagem, Usuario usuario) throws Exception {
        Map<String, Object> consultarProcesso = Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "consultar_processo",
                        "description", "Consulta o status de um processo.",
                        "parameters", Map.of("type", "object",
                                "properties", Map.of("processoId", Map.of("type", "integer")),
                                "required", List.of("processoId"))));
        Map<String, Object> proporNotificacao = Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "propor_notificacao_whatsapp",
                        "description", "Propõe uma notificação WhatsApp; não envia.",
                        "parameters", Map.of("type", "object",
                                "properties", Map.of(
                                        "processoId", Map.of("type", "integer"),
                                        "telefone", Map.of("type", "string"),
                                        "mensagem", Map.of("type", "string")),
                                "required", List.of("processoId", "telefone", "mensagem"))));
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", "Você é o assistente operacional do SystemX. Nunca execute ações; use ferramentas apenas para consultar processo ou propor notificação WhatsApp. Não invente IDs, telefones ou dados. Para notificar, peça telefone e mensagem explícitos."),
                        Map.of("role", "user", "content", "Usuário " + usuario.getId() + ": " + mensagem)),
                "tools", List.of(consultarProcesso, proporNotificacao));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Groq respondeu " + response.statusCode());
        return json.readTree(response.body());
    }

    private Usuario usuarioAtual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarios.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Usuário autenticado não encontrado."));
    }

    private record Proposal(Long userId, Long processoId, String telefone, String mensagem, Instant expiresAt) { }
}
