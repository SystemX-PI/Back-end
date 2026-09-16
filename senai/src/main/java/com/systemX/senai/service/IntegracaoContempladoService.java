package com.systemX.senai.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.dto.ComunicacaoResponse;
import com.systemX.senai.dto.ContempladoRequest;
import com.systemX.senai.dto.IntegracaoResponse;
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
import com.systemX.senai.exception.InvalidCredentialsException;
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

    public IntegracaoContempladoService(
            UsuarioRepository usuarioRepository,
            CotaRepository cotaRepository,
            AssembleiaRepository assembleiaRepository,
            ParticipacaoCotaRepository participacaoRepository,
            ContemplacaoRepository contemplacaoRepository,
            ProcessoContemplacaoRepository processoRepository,
            ComunicacaoRepository comunicacaoRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.cotaRepository = cotaRepository;
        this.assembleiaRepository = assembleiaRepository;
        this.participacaoRepository = participacaoRepository;
        this.contemplacaoRepository = contemplacaoRepository;
        this.processoRepository = processoRepository;
        this.comunicacaoRepository = comunicacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public IntegracaoResponse receber(ContempladoRequest request) {
        var existente = processoRepository.findByOrigemAndIdExterno(request.origem(), request.idExterno());
        if (existente.isPresent()) {
            ProcessoContemplacao processo = existente.get();
                Comunicacao comunicacao = comunicacaoRepository.findByProcessoId(processo.getId()).orElse(null);
            return response(processo, comunicacao, true);
        }

        Usuario cliente = usuarioRepository.findByEmail(request.email()).orElseGet(() -> criarCliente(request));
        var cota = cotaRepository.findById(request.cotaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cota não encontrada: " + request.cotaId()));
        Assembleia assembleia = assembleiaRepository.findById(request.assembleiaId())
                .orElseThrow(() -> new ResourceNotFoundException("Assembleia não encontrada: " + request.assembleiaId()));
        Usuario responsavel = usuarioRepository.findFirstByAtivoTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum usuário ativo para registrar a comunicação"));

        if (participacaoRepository.findByUsuarioIdAndCotaId(cliente.getId(), cota.getId()).isEmpty()) {
                ParticipacaoCota participacao = ParticipacaoCota.criar(cliente, cota, LocalDate.now());
            participacaoRepository.save(participacao);
        }

        BigDecimal valor = request.valorCredito() == null ? cota.getValorCredito() : request.valorCredito();
        Contemplacao contemplacao = Contemplacao.criar(cota, assembleia, request.dataContemplacao(), request.tipo(), valor);
        contemplacaoRepository.save(contemplacao);

        ProcessoContemplacao processo = new ProcessoContemplacao();
        processo.setIdExterno(request.idExterno());
        processo.setOrigem(request.origem());
        processo.setContemplacao(contemplacao);
        processo.setResponsavel(responsavel);
        processo.setStatus(StatusProcesso.AGUARDANDO_COMUNICACAO);
        processo.setAtualizadoEm(LocalDateTime.now());
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
        comunicacao.setCriadaEm(LocalDateTime.now());
        comunicacaoRepository.save(comunicacao);

        enviar(comunicacao);
        return response(processo, comunicacao, false);
    }

    @Transactional
    public ComunicacaoResponse reenviar(Long processoId) {
        Comunicacao comunicacao = comunicacaoRepository.findByProcessoId(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Comunicação não encontrada para o processo: " + processoId));
        enviar(comunicacao);
        return toResponse(comunicacao);
    }

    @Transactional(readOnly = true)
    public ComunicacaoResponse consultar(Long processoId) {
        return comunicacaoRepository.findByProcessoId(processoId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Comunicação não encontrada para o processo: " + processoId));
    }

    private Usuario criarCliente(ContempladoRequest request) {
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setSenha(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        usuario.setTipo(TipoUsuario.CLIENTE);
        return usuarioRepository.save(usuario);
    }

    private void enviar(Comunicacao comunicacao) {
        if (!emailService.isEnabled()) {
            comunicacao.setErro("E-mail desabilitado. Configure app.mail.enabled=true.");
            comunicacao.setStatus(StatusComunicacao.PENDENTE);
            comunicacaoRepository.save(comunicacao);
            return;
        }
        try {
            emailService.send(comunicacao.getDestinatario(), comunicacao.getAssunto(), comunicacao.getMensagem());
            comunicacao.setStatus(StatusComunicacao.SUCESSO);
            comunicacao.setEnviadaEm(LocalDateTime.now());
            comunicacao.setErro(null);
        } catch (RuntimeException exception) {
            comunicacao.setStatus(StatusComunicacao.FALHA);
            comunicacao.setErro(exception.getMessage());
        }
        comunicacaoRepository.save(comunicacao);
    }

    private IntegracaoResponse response(ProcessoContemplacao processo, Comunicacao comunicacao, boolean existente) {
        return new IntegracaoResponse(processo.getId(), comunicacao == null ? null : comunicacao.getId(),
                processo.getOrigem(), processo.getIdExterno(), processo.getStatus().name(),
                comunicacao == null ? null : comunicacao.getStatus().name(), existente);
    }

    private ComunicacaoResponse toResponse(Comunicacao comunicacao) {
        return new ComunicacaoResponse(comunicacao.getId(), comunicacao.getProcesso().getId(),
                comunicacao.getCanal().name(), comunicacao.getDestinatario(), comunicacao.getStatus().name(),
                comunicacao.getErro(), comunicacao.getCriadaEm(), comunicacao.getEnviadaEm());
    }
}