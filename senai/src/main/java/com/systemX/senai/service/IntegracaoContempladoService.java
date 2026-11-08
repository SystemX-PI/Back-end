package com.systemX.senai.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
            WhatsAppService whatsAppService, AuditoriaService auditoria, PlatformService platform) {
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
        this.platform = platform;
    }

    @Transactional
    public IntegracaoResponse receber(ContempladoRequest request) {
        var existente = processoRepository.findByOrigemAndIdExterno(request.origem(), request.idExterno());
        if (existente.isPresent()) {
            ProcessoContemplacao processo = existente.get();
                Comunicacao comunicacao = comunicacaoRepository
                    .findFirstByProcessoIdAndCanalOrderByIdDesc(processo.getId(), CanalComunicacao.EMAIL)
                    .orElse(null);
                Comunicacao comunicacaoWhatsApp = comunicacaoRepository
                    .findFirstByProcessoIdAndCanalOrderByIdDesc(processo.getId(), CanalComunicacao.WHATSAPP)
                    .orElse(null);
                return response(processo, comunicacao, comunicacaoWhatsApp, true);
        }

        Usuario cliente = usuarioRepository.findByEmail(request.email()).orElseGet(() -> criarCliente(request));
        var cota = cotaRepository.findById(request.cotaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cota não encontrada: " + request.cotaId()));
        Assembleia assembleia = assembleiaRepository.findById(request.assembleiaId())
                .orElseThrow(() -> new ResourceNotFoundException("Assembleia não encontrada: " + request.assembleiaId()));
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
        comunicacao.setAssunto(assunto);
        comunicacao.setMensagem(mensagem);
        comunicacao.setCriadaEm(LocalDateTime.now(ZoneOffset.UTC));
        comunicacaoRepository.save(comunicacao);

        Comunicacao comunicacaoWhatsApp = null;
        if (request.telefone() != null && !request.telefone().isBlank()) {
            comunicacaoWhatsApp = criarWhatsApp(processo, responsavel, request.telefone(), mensagem);
        }
        return response(processo, comunicacao, comunicacaoWhatsApp, false);
    }

    @Transactional(readOnly = true)
    public List<FilaComunicacaoResponse> listarFila() { return listarFilaInterna(0, 100, "", "").items(); }

    @Transactional(readOnly = true)
    public com.systemX.senai.dto.PaginaResponse<FilaComunicacaoResponse> listarFilaPagina(int page, int size, String q, String status) {
        return listarFilaInterna(page, size, q, status);
    }

    private com.systemX.senai.dto.PaginaResponse<FilaComunicacaoResponse> listarFilaInterna(int page, int size, String q, String status) {
        var processos = processoRepository.findAll(FilaFiltro.criar(q, status), Paginacao.pagina(page, size));
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

    @Transactional
    public ComunicacaoResponse reenviar(Long processoId) {
        ProcessoContemplacao processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        Comunicacao email = comunicacaoRepository
                .findFirstByProcessoIdAndCanalOrderByIdDesc(processoId, CanalComunicacao.EMAIL)
                .orElse(null);
        Comunicacao whatsapp = comunicacaoRepository
                .findFirstByProcessoIdAndCanalOrderByIdDesc(processoId, CanalComunicacao.WHATSAPP)
                .orElse(null);
        if (email == null && whatsapp == null) {
            throw new ResourceNotFoundException("Comunicação não encontrada para o processo: " + processoId);
        }
        if (email != null) {
            processo.setStatus(StatusProcesso.EM_COMUNICACAO);
            processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
            processoRepository.save(processo);
            enviar(email);
        }
        if (whatsapp != null) {
            enviar(whatsapp);
        }
        boolean comunicado = (email != null && email.getStatus() == StatusComunicacao.SUCESSO)
                || (whatsapp != null && whatsapp.getStatus() == StatusComunicacao.SUCESSO);
        processo.setStatus(comunicado ? StatusProcesso.CLIENTE_CONTATADO : StatusProcesso.AGUARDANDO_COMUNICACAO);
        processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
        processoRepository.save(processo);
        return toResponse(processoId, email, whatsapp);
    }

    @Transactional(readOnly = true)
    public ComunicacaoResponse consultar(Long processoId) {
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

        @Transactional
        public ComunicacaoResponse enviarWhatsApp(Long processoId, WhatsAppRequest request) {
        ProcessoContemplacao processo = processoRepository.findById(processoId)
            .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        String remoteName = request.remetenteId() == null ? null
            : platform.connectedInternalWhatsApp(request.remetenteId()).getRemoteName();
        Usuario responsavel = auditoria.responsavel();
        if (responsavel == null) responsavel = usuarioRepository.findFirstByAtivoTrue()
            .orElseThrow(() -> new ResourceNotFoundException("Nenhum usuário ativo para registrar a comunicação"));
        Comunicacao comunicacao = criarWhatsApp(processo, responsavel, request.telefone(), request.mensagem());
        enviar(comunicacao, remoteName);
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
        comunicacao.setCriadaEm(LocalDateTime.now(ZoneOffset.UTC));
        return comunicacaoRepository.save(comunicacao);
    }

    private Usuario criarCliente(ContempladoRequest request) {
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setTelefone(request.telefone());
        usuario.setSenha(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        usuario.setTipo(TipoUsuario.CLIENTE);
        return usuarioRepository.save(usuario);
    }

    private void enviar(Comunicacao comunicacao) {
        enviar(comunicacao, null);
    }

    private void enviar(Comunicacao comunicacao, String remoteName) {
        boolean whatsApp = comunicacao.getCanal() == CanalComunicacao.WHATSAPP;
        if (whatsApp && remoteName == null && !whatsAppService.isEnabled()) {
            comunicacao.setErro("WhatsApp desabilitado. Configure app.whatsapp.enabled=true.");
            comunicacao.setStatus(StatusComunicacao.PENDENTE);
            comunicacaoRepository.save(comunicacao);
            return;
        }
        if (!whatsApp && !emailService.isEnabled()) {
            comunicacao.setErro("E-mail desabilitado. Configure app.mail.enabled=true.");
            comunicacao.setStatus(StatusComunicacao.PENDENTE);
            comunicacaoRepository.save(comunicacao);
            return;
        }
        try {
            if (remoteName != null) {
                whatsAppService.sendFrom(remoteName, comunicacao.getDestinatario(), comunicacao.getMensagem());
            } else if (whatsApp) {
                whatsAppService.send(comunicacao.getDestinatario(), comunicacao.getMensagem());
            } else {
                emailService.send(comunicacao.getDestinatario(), comunicacao.getAssunto(), comunicacao.getMensagem());
            }
            comunicacao.setStatus(StatusComunicacao.SUCESSO);
            comunicacao.setEnviadaEm(LocalDateTime.now(ZoneOffset.UTC));
            comunicacao.setErro(null);
        } catch (RuntimeException exception) {
            comunicacao.setStatus(StatusComunicacao.FALHA);
            if (remoteName == null) comunicacao.setErro(exception.getMessage());
            else if (exception instanceof org.springframework.web.server.ResponseStatusException providerError)
                comunicacao.setErro(providerError.getReason());
            else comunicacao.setErro("Não foi possível enviar pelo WhatsApp selecionado.");
        }
        comunicacaoRepository.save(comunicacao);
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
        boolean emailSucesso = email != null && email.getStatus() == StatusComunicacao.SUCESSO;
        boolean whatsappSucesso = whatsapp != null && whatsapp.getStatus() == StatusComunicacao.SUCESSO;
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
                comunicacao.getStatus().name(), comunicacao.getErro(), comunicacao.getCriadaEm(),
                comunicacao.getEnviadaEm());
    }
}
