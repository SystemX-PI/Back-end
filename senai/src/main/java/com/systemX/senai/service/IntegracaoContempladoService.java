package com.systemX.senai.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.dto.ComunicacaoResponse;
import com.systemX.senai.dto.CanalComunicacaoResponse;
import com.systemX.senai.dto.ContempladoRequest;
import com.systemX.senai.dto.IntegracaoResponse;
import com.systemX.senai.dto.FilaComunicacaoResponse;
import com.systemX.senai.dto.WhatsAppRequest;
import com.systemX.senai.entity.Assembleia;
import com.systemX.senai.entity.Comunicacao;
import com.systemX.senai.entity.Contemplacao;
import com.systemX.senai.entity.ParticipacaoCota;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.CanalComunicacao;
import com.systemX.senai.enums.StatusComunicacao;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.AssembleiaRepository;
import com.systemX.senai.repository.ComunicacaoRepository;
import com.systemX.senai.repository.ContemplacaoRepository;
import com.systemX.senai.repository.CotaRepository;
import com.systemX.senai.repository.ParticipacaoCotaRepository;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;
import com.systemX.senai.repository.UsuarioRepository;

@Service
public class IntegracaoContempladoService {
    private final UsuarioRepository usuarioRepository;
    private final CotaRepository cotaRepository;
    private final AssembleiaRepository assembleiaRepository;
    private final ParticipacaoCotaRepository participacaoRepository;
    private final ContemplacaoRepository contemplacaoRepository;
    private final ProcessoContemplacaoRepository processoRepository;
    private final ComunicacaoRepository comunicacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final AuditoriaService auditoria;
    private final OperacaoAcesso acesso;
    private final com.systemX.senai.repository.HistoricoComunicacaoRepository historico;
    private final PlatformService platform;

    public IntegracaoContempladoService(
            UsuarioRepository usuarioRepository,
            CotaRepository cotaRepository,
            AssembleiaRepository assembleiaRepository,
            ParticipacaoCotaRepository participacaoRepository,
            ContemplacaoRepository contemplacaoRepository,
            ProcessoContemplacaoRepository processoRepository,
            ComunicacaoRepository comunicacaoRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            WhatsAppService whatsAppService, AuditoriaService auditoria, PlatformService platform, OperacaoAcesso acesso,
            com.systemX.senai.repository.HistoricoComunicacaoRepository historico) {
        this.usuarioRepository = usuarioRepository;
        this.cotaRepository = cotaRepository;
        this.assembleiaRepository = assembleiaRepository;
        this.participacaoRepository = participacaoRepository;
        this.contemplacaoRepository = contemplacaoRepository;
        this.processoRepository = processoRepository;
        this.comunicacaoRepository = comunicacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.historico = historico;
        this.platform = platform;
    }

    // Reentregas com a mesma cota observam o commit anterior antes de consultar a chave externa.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public IntegracaoResponse receber(ContempladoRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        var cota = cotaRepository.bloquearPorId(request.cotaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cota não encontrada."));
        var existente = processoRepository.findByOrigemAndIdExterno(request.origem(), request.idExterno());
        if (existente.isPresent()) {
            var processo = existente.get();
            var cliente = processo.getCliente();
            var contemplada = processo.getContemplacao();
            if (cliente == null || !email.equalsIgnoreCase(cliente.getEmail())
                    || !request.cotaId().equals(contemplada.getCota().getId())
                    || !request.assembleiaId().equals(contemplada.getAssembleia().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Evento de integração em conflito.");
            }
            var comunicacao = comunicacaoRepository
                    .findFirstByProcessoIdAndCanalOrderByIdDesc(processo.getId(), CanalComunicacao.EMAIL).orElse(null);
            var whatsapp = comunicacaoRepository
                    .findFirstByProcessoIdAndCanalOrderByIdDesc(processo.getId(), CanalComunicacao.WHATSAPP).orElse(null);
            return response(processo, comunicacao, whatsapp, true);
        }

        Assembleia assembleia = assembleiaRepository.findById(request.assembleiaId())
                .orElseThrow(() -> new ResourceNotFoundException("Assembleia não encontrada."));
        if (!cota.getGrupo().getId().equals(assembleia.getGrupo().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cota e assembleia devem pertencer ao mesmo grupo.");
        }
        Usuario cliente = usuarioRepository.findByEmail(email).orElseGet(() -> criarCliente(request));
        Usuario responsavel = usuarioRepository.findFirstByAtivoTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum usuário ativo para registrar a comunicação"));

        if (participacaoRepository.findByUsuarioIdAndCotaId(cliente.getId(), cota.getId()).isEmpty()) {
                ParticipacaoCota participacao = ParticipacaoCota.criar(cliente, cota, LocalDate.now(ZoneOffset.UTC));
            participacaoRepository.save(participacao);
        }

        BigDecimal valor = request.valorCredito() == null ? cota.getValorCredito() : request.valorCredito();
        Contemplacao contemplacao = Contemplacao.criar(cota, assembleia, request.dataContemplacao(), request.tipo(), valor);
        contemplacaoRepository.save(contemplacao);

        ProcessoContemplacao processo = new ProcessoContemplacao();
        processo.setIdExterno(request.idExterno());
        processo.setOrigem(request.origem());
        processo.setContemplacao(contemplacao);
        processo.setCliente(cliente);
        processo.setResponsavel(responsavel);
        processo.setStatus(StatusProcesso.AGUARDANDO_COMUNICACAO);
        processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
        processoRepository.save(processo);

        String assunto = "Sua contemplação foi confirmada";
        String mensagem = "Olá, " + cliente.getNome() + ", sua contemplação foi registrada. "
                + "Acesse o atendimento para consultar os próximos passos.";
        Comunicacao comunicacao = new Comunicacao();
        comunicacao.setProcesso(processo);
        comunicacao.setCanal(CanalComunicacao.EMAIL);
        comunicacao.setStatus(StatusComunicacao.PENDENTE);
        comunicacao.setResponsavel(responsavel);
        comunicacao.setDestinatario(cliente.getEmail());
        comunicacao.setComunicadoOficial(true);
        comunicacao.setAssunto(assunto);
        comunicacao.setMensagem(mensagem);
        comunicacao.setCriadaEm(agora());
        comunicacaoRepository.save(comunicacao);

        Comunicacao comunicacaoWhatsApp = null;
        if (request.telefone() != null && !request.telefone().isBlank()) {
            comunicacaoWhatsApp = criarWhatsApp(processo, responsavel, request.telefone(), mensagem);
            comunicacaoWhatsApp.setComunicadoOficial(true);
        }
        return response(processo, comunicacao, comunicacaoWhatsApp, false);
    }

    @Transactional(readOnly = true)
    public List<FilaComunicacaoResponse> listarFila() { return listarFilaInterna(0, 100, "", "").items(); }

    @Transactional(readOnly = true)
    public com.systemX.senai.dto.PaginaResponse<FilaComunicacaoResponse> listarFilaPagina(int page, int size, String q, String status) {
        return listarFilaInterna(page, size, q, status);
    }

    public List<java.util.Map<String, Object>> remetentesWhatsApp() {
        return platform.operationalWhatsAppSenders();
    }

    public record StatusRemetente(boolean connected) {}

    public StatusRemetente statusRemetente(Long senderId) {
        String defaultInstance = whatsAppService.isEnabled() ? whatsAppService.defaultInstance() : null;
        return new StatusRemetente(platform.operationalWhatsAppConnected(senderId, defaultInstance));
    }

    public record ResumoFila(long total, long pendentes, long comunicados, long comFalha) {}

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ResumoFila resumoFila() {
        var escopo = acesso.processosVisiveis();
        long total = processoRepository.count(escopo.and(FilaFiltro.criar("", "")));
        long pendentes = processoRepository.count(escopo.and(FilaFiltro.criar("", "PENDENTE")));
        long comFalha = processoRepository.count(escopo.and(FilaFiltro.criar("", "FALHA")));
        return new ResumoFila(total, pendentes, total - pendentes, comFalha);
    }

    private com.systemX.senai.dto.PaginaResponse<FilaComunicacaoResponse> listarFilaInterna(int page, int size, String q, String status) {
        var processos = processoRepository.findAll(acesso.processosVisiveis().and(FilaFiltro.criar(q, status)), Paginacao.pagina(page, size));
        var ids = processos.getContent().stream().map(ProcessoContemplacao::getId).toList();
        var canais = ids.isEmpty() ? List.<Comunicacao>of() : comunicacaoRepository.buscarUltimasEmLote(ids);
        var porProcesso = canais.stream().collect(java.util.stream.Collectors.groupingBy(c -> c.getProcesso().getId()));
        return com.systemX.senai.dto.PaginaResponse.from(processos.map(processo -> {
            var registros = porProcesso.getOrDefault(processo.getId(), List.of());
            Comunicacao email = registros.stream().filter(c -> c.getCanal() == CanalComunicacao.EMAIL).findFirst().orElse(null);
            Comunicacao whatsapp = registros.stream().filter(c -> c.getCanal() == CanalComunicacao.WHATSAPP).findFirst().orElse(null);
            return new FilaComunicacaoResponse(processo.getId(), processo.getOrigem(), processo.getIdExterno(),
                processo.getStatus().name(), processo.getCriadoEm(), toResponse(processo.getId(), email, whatsapp));
        }));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ComunicacaoResponse reenviar(Long processoId, java.util.UUID chave) {
        ProcessoContemplacao processo = processoRepository.bloquearPorId(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        acesso.conferirProcesso(processo);
        Usuario responsavel = auditoria.exigirOperadorGlobal();
        String operacao = chave == null ? null : "oficial:" + chave;
        var anterior = tentativas(processoId, operacao);
        if (!anterior.isEmpty()) return toResponse(processoId, anterior);
        Comunicacao originalEmail = comunicacaoRepository
                .findFirstByProcessoIdAndCanalOrderByIdAsc(processoId, CanalComunicacao.EMAIL)
                .orElseThrow(() -> new ResourceNotFoundException("Comunicado padrão não encontrado para o processo: " + processoId));
        Comunicacao originalWhatsApp = comunicacaoRepository
                .findFirstByProcessoIdAndCanalAndComunicadoOficialTrueOrderByIdAsc(processoId, CanalComunicacao.WHATSAPP)
                .orElseGet(() -> originalEmail.isComunicadoOficial() ? null : comunicacaoRepository
                        .findFirstByProcessoIdAndCanalAndMensagemOrderByIdAsc(
                                processoId, CanalComunicacao.WHATSAPP, originalEmail.getMensagem())
                        .orElse(null));

        // Promove apenas o marcador da comunicação legada identificada, sem alterar conteúdo ou estado.
        if (originalWhatsApp != null) {
            originalWhatsApp.setComunicadoOficial(true);
            originalEmail.setComunicadoOficial(true);
        }

        Comunicacao email = reenviarCanal(originalEmail, responsavel, operacao);
        Comunicacao whatsapp = originalWhatsApp == null ? null : reenviarCanal(originalWhatsApp, responsavel, operacao);
        atualizarAposComunicacao(processo, email, whatsapp);
        return toResponse(processoId, email, whatsapp);
    }

    private Comunicacao reenviarCanal(Comunicacao original, Usuario responsavel, String chave) {
        Comunicacao tentativa = new Comunicacao();
        tentativa.setComunicadoOriginal(original);
        tentativa.setChaveOperacao(chave);
        tentativa.setProcesso(original.getProcesso());
        tentativa.setCanal(original.getCanal());
        tentativa.setStatus(StatusComunicacao.PENDENTE);
        tentativa.setResponsavel(responsavel);
        tentativa.setDestinatario(original.getDestinatario());
        tentativa.setAssunto(original.getAssunto());
        tentativa.setMensagem(original.getMensagem());
        tentativa.setCriadaEm(agora());
        enviar(tentativa);
        return tentativa;
    }

    private void atualizarAposComunicacao(ProcessoContemplacao processo, Comunicacao email, Comunicacao whatsapp) {
        switch (processo.getStatus()) {
            case CONTEMPLADO, AGUARDANDO_COMUNICACAO, EM_COMUNICACAO -> {
                boolean comunicado = (email != null && email.getStatus().aceito())
                        || (whatsapp != null && whatsapp.getStatus().aceito());
                processo.setStatus(comunicado ? StatusProcesso.CLIENTE_CONTATADO : StatusProcesso.AGUARDANDO_COMUNICACAO);
                processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
                processoRepository.save(processo);
            }
            default -> { /* Preserva o andamento após o primeiro contato. */ }
        }
    }

    @Transactional(readOnly = true)
    public ComunicacaoResponse consultar(Long processoId) {
        acesso.conferirProcesso(processoRepository.findById(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId)));
        Comunicacao email = comunicacaoRepository
            .findFirstByProcessoIdAndCanalOrderByIdDesc(processoId, CanalComunicacao.EMAIL)
            .orElse(null);
        Comunicacao whatsapp = comunicacaoRepository
            .findFirstByProcessoIdAndCanalOrderByIdDesc(processoId, CanalComunicacao.WHATSAPP)
            .orElse(null);
        if (email == null && whatsapp == null) {
            throw new ResourceNotFoundException("Comunicação não encontrada para o processo: " + processoId);
        }
        return toResponse(processoId, email, whatsapp);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ComunicacaoResponse enviarWhatsApp(Long processoId, WhatsAppRequest request, java.util.UUID chave) {
        ProcessoContemplacao processo = processoRepository.bloquearPorId(processoId)
            .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        acesso.conferirProcesso(processo);
        Usuario responsavel = auditoria.exigirOperadorGlobal();
        String operacao = chave == null ? null : "manual:" + chave;
        var anterior = tentativas(processoId, operacao);
        if (!anterior.isEmpty()) {
            var tentativa = anterior.get(0);
            if (!java.util.Objects.equals(tentativa.getDestinatario(), request.telefone())
                    || !java.util.Objects.equals(tentativa.getMensagem(), request.mensagem())
                    || !java.util.Objects.equals(tentativa.getRemetenteSolicitadoId(), request.remetenteId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta chave já identifica outro envio.");
            }
            return toResponse(tentativa);
        }
        String remoteName = request.remetenteId() == null ? null
            : platform.connectedInternalWhatsApp(request.remetenteId()).getRemoteName();
        Comunicacao comunicacao = criarWhatsApp(processo, responsavel, request.telefone(), request.mensagem());
        comunicacao.setChaveOperacao(operacao);
        comunicacao.setRemetenteSolicitadoId(request.remetenteId());
        enviar(comunicacao, remoteName);
        if (comunicacao.getStatus().aceito()) atualizarAposComunicacao(processo, null, comunicacao);
        auditoria.registrar("Disparo manual de WhatsApp", "Processo", processoId, comunicacao.getStatus().name());
        return toResponse(comunicacao);
        }

    private Comunicacao criarWhatsApp(ProcessoContemplacao processo, Usuario responsavel,
            String telefone, String mensagem) {
        Comunicacao comunicacao = new Comunicacao();
        comunicacao.setProcesso(processo);
        comunicacao.setCanal(CanalComunicacao.WHATSAPP);
        comunicacao.setStatus(StatusComunicacao.PENDENTE);
        comunicacao.setResponsavel(responsavel);
        comunicacao.setDestinatario(telefone);
        comunicacao.setMensagem(mensagem);
        comunicacao.setCriadaEm(agora());
        return comunicacaoRepository.save(comunicacao);
    }

    private Usuario criarCliente(ContempladoRequest request) {
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT));
        usuario.setTelefone(request.telefone());
        usuario.setSenha(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        usuario.setTipo(TipoUsuario.CLIENTE);
        return usuarioRepository.save(usuario);
    }

    private List<Comunicacao> tentativas(Long processoId, String chave) {
        return chave == null ? List.of() : comunicacaoRepository.findByProcessoIdAndChaveOperacao(processoId, chave);
    }

    private ComunicacaoResponse toResponse(Long processoId, List<Comunicacao> tentativas) {
        var email = tentativas.stream().filter(c -> c.getCanal() == CanalComunicacao.EMAIL).findFirst().orElse(null);
        var whatsapp = tentativas.stream().filter(c -> c.getCanal() == CanalComunicacao.WHATSAPP).findFirst().orElse(null);
        return toResponse(processoId, email, whatsapp);
    }

    private void enviar(Comunicacao comunicacao) {
        enviar(comunicacao, null);
    }

    private void enviar(Comunicacao comunicacao, String remoteName) {
        boolean whatsApp = comunicacao.getCanal() == CanalComunicacao.WHATSAPP;
        if (whatsApp && remoteName == null && !whatsAppService.isEnabled()) {
            comunicacao.setErro("Canal WhatsApp indisponível.");
            comunicacao.setStatus(StatusComunicacao.PENDENTE);
            registrarTentativa(comunicacao);
            return;
        }
        if (!whatsApp && !emailService.isEnabled()) {
            comunicacao.setErro("Canal de e-mail indisponível.");
            comunicacao.setStatus(StatusComunicacao.PENDENTE);
            registrarTentativa(comunicacao);
            return;
        }
        try {
            if (remoteName != null) {
                comunicacao.setProvedorMensagemId(whatsAppService.sendFrom(
                        remoteName, comunicacao.getDestinatario(), comunicacao.getMensagem()));
            } else if (whatsApp) {
                comunicacao.setProvedorMensagemId(whatsAppService.send(
                        comunicacao.getDestinatario(), comunicacao.getMensagem()));
            } else {
                emailService.send(comunicacao.getDestinatario(), comunicacao.getAssunto(), comunicacao.getMensagem());
            }
            comunicacao.setStatus(StatusComunicacao.SUCESSO);
            comunicacao.setEnviadaEm(agora());
            comunicacao.setErro(null);
        } catch (RuntimeException exception) {
            boolean semResposta = exception.getCause() instanceof org.springframework.web.client.ResourceAccessException
                    || exception.getCause() instanceof java.net.SocketTimeoutException;
            comunicacao.setStatus(semResposta ? StatusComunicacao.SEM_RESPOSTA : StatusComunicacao.FALHA);
            comunicacao.setErro(semResposta ? "Resultado do provedor não confirmado." : "Falha na tentativa de envio.");
        }
        registrarTentativa(comunicacao);
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    private void registrarTentativa(Comunicacao comunicacao) {
        comunicacaoRepository.save(comunicacao);
        historico.save(com.systemX.senai.entity.HistoricoComunicacao.builder()
                .comunicacao(comunicacao)
                .canal(comunicacao.getCanal())
                .usuario(comunicacao.getResponsavel())
                .status(comunicacao.getStatus())
                .data(LocalDateTime.now(ZoneOffset.UTC))
                .resposta(NotificationHistoryService.publicError(comunicacao))
                .build());
    }

    private IntegracaoResponse response(ProcessoContemplacao processo, Comunicacao comunicacao,
            Comunicacao comunicacaoWhatsApp, boolean existente) {
        return new IntegracaoResponse(processo.getId(), comunicacao == null ? null : comunicacao.getId(),
                processo.getOrigem(), processo.getIdExterno(), processo.getStatus().name(),
                comunicacao == null ? null : comunicacao.getStatus().name(), existente,
                comunicacaoWhatsApp == null ? null : comunicacaoWhatsApp.getId(),
                comunicacaoWhatsApp == null ? null : comunicacaoWhatsApp.getStatus().name());
    }

    private ComunicacaoResponse toResponse(Comunicacao comunicacao) {
        Comunicacao email = comunicacao.getCanal() == CanalComunicacao.EMAIL ? comunicacao : null;
        Comunicacao whatsapp = comunicacao.getCanal() == CanalComunicacao.WHATSAPP ? comunicacao : null;
        return toResponse(comunicacao.getProcesso().getId(), email, whatsapp);
    }

    private ComunicacaoResponse toResponse(Long processoId, Comunicacao email, Comunicacao whatsapp) {
        boolean emailSucesso = email != null && email.getStatus().aceito();
        boolean whatsappSucesso = whatsapp != null && whatsapp.getStatus().aceito();
        String statusGeral;
        if (emailSucesso && whatsappSucesso) {
            statusGeral = "COMUNICADO_NOS_DOIS";
        } else if (emailSucesso || whatsappSucesso) {
            statusGeral = "COMUNICADO_PARCIAL";
        } else {
            statusGeral = "PENDENTE";
        }
        return new ComunicacaoResponse(processoId, statusGeral,
                canalResponse(email), canalResponse(whatsapp));
    }

    private CanalComunicacaoResponse canalResponse(Comunicacao comunicacao) {
        if (comunicacao == null) {
            return null;
        }
        return new CanalComunicacaoResponse(comunicacao.getId(), comunicacao.getDestinatario(),
                comunicacao.getStatus().name(), NotificationHistoryService.publicError(comunicacao), comunicacao.getCriadaEm(),
                comunicacao.getEnviadaEm());
    }
}
