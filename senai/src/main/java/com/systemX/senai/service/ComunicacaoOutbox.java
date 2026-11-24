package com.systemX.senai.service;

import com.systemX.senai.dto.ReconciliarComunicacaoRequest;
import com.systemX.senai.entity.Comunicacao;
import com.systemX.senai.entity.HistoricoComunicacao;
import com.systemX.senai.enums.CanalComunicacao;
import com.systemX.senai.enums.EstadoDespacho;
import com.systemX.senai.enums.StatusComunicacao;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.ComunicacaoRepository;
import com.systemX.senai.repository.HistoricoComunicacaoRepository;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComunicacaoOutbox {
    private static final Logger LOG = LoggerFactory.getLogger(ComunicacaoOutbox.class);
    private static final long MINUTOS_PARA_SINALIZAR = 15;
    private static final PageRequest LOTE = PageRequest.of(0, 20);
    private final ComunicacaoRepository comunicacoes;
    private final HistoricoComunicacaoRepository historico;
    private final ProcessoContemplacaoRepository processos;
    private final EmailService email;
    private final WhatsAppService whatsapp;
    private final AuditoriaService auditoria;
    private final OperacaoAcesso acesso;
    private final TransactionTemplate transacao;

    public ComunicacaoOutbox(ComunicacaoRepository comunicacoes, HistoricoComunicacaoRepository historico,
            ProcessoContemplacaoRepository processos, EmailService email, WhatsAppService whatsapp,
            AuditoriaService auditoria, OperacaoAcesso acesso, TransactionTemplate transacao) {
        this.comunicacoes = comunicacoes;
        this.historico = historico;
        this.processos = processos;
        this.email = email;
        this.whatsapp = whatsapp;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.transacao = transacao;
    }

    private record Envio(CanalComunicacao canal, String destinatario, String assunto, String mensagem, String instancia) {}
    private record Resultado(StatusComunicacao status, EstadoDespacho estado, String idProvedor, String detalhe) {}

    @Scheduled(fixedDelayString = "${app.notifications.outbox.poll-ms:3000}",
               initialDelayString = "${app.notifications.outbox.initial-delay-ms:3000}")
    public void processarFila() {
        exigirForaDeTransacao();
        var limite = agora().minusMinutes(MINUTOS_PARA_SINALIZAR);
        for (Long id : comunicacoes.idsExpirados(EstadoDespacho.EM_ENVIO, limite, LOTE)) {
            try {
                sinalizarExpiracao(id, limite);
            } catch (RuntimeException ex) {
                LOG.error("Não foi possível sinalizar a tentativa de comunicação expirada {}.", id);
            }
        }
        for (Long id : comunicacoes.idsDespachaveis(EstadoDespacho.NA_FILA,
                CanalComunicacao.EMAIL, email.isEnabled(), CanalComunicacao.WHATSAPP,
                whatsapp.isEnabled(), whatsapp.isConfigured(), LOTE)) {
            try {
                processar(id);
            } catch (RuntimeException ex) {
                LOG.error("Não foi possível processar a tentativa de comunicação {}.", id);
            }
        }
    }

    public void processar(Long id) {
        exigirForaDeTransacao();
        // O registro da reserva é confirmado antes de qualquer chamada ao provedor.
        Envio envio = transacao.execute(tx -> reservar(id));
        if (envio == null) return;
        Resultado resultado = enviar(envio);
        try {
            finalizarExecucao(id, resultado);
        } catch (RuntimeException ex) {
            LOG.error("Tentativa de comunicação {} sem confirmação local. Requer reconciliação.", id);
            finalizarExecucao(id, incerto());
        }
    }

    private void finalizarExecucao(Long id, Resultado resultado) {
        transacao.executeWithoutResult(tx -> comunicacoes.bloquearTentativa(id)
                .filter(c -> c.getEstadoDespacho() == EstadoDespacho.EM_ENVIO
                        || c.getEstadoDespacho() == EstadoDespacho.INCERTO_EXECUCAO)
                .ifPresent(c -> registrarResultado(c, resultado, null)));
    }

    private Envio reservar(Long id) {
        var processoId = comunicacoes.processoDaTentativa(id).orElse(null);
        if (processoId == null) return null;
        var processo = processos.bloquearPorId(processoId).orElseThrow();
        var tentativa = comunicacoes.bloquearTentativa(id).orElseThrow();
        if (tentativa.getEstadoDespacho() != EstadoDespacho.NA_FILA) return null;
        if (processo.getStatus() == StatusProcesso.CANCELADO) {
            registrarResultado(tentativa, concluido(StatusComunicacao.FALHA, null,
                    "Processo cancelado antes do despacho."), null);
            return null;
        }
        if (!canalDisponivel(tentativa)) return null;
        tentativa.setEstadoDespacho(EstadoDespacho.EM_ENVIO);
        tentativa.setDespachoIniciadoEm(agora());
        return new Envio(tentativa.getCanal(), tentativa.getDestinatario(), tentativa.getAssunto(),
                tentativa.getMensagem(), tentativa.getInstanciaDespacho());
    }

    private boolean canalDisponivel(Comunicacao tentativa) {
        if (tentativa.getCanal() == CanalComunicacao.EMAIL) return email.isEnabled();
        return tentativa.getInstanciaDespacho() == null ? whatsapp.isEnabled() : whatsapp.isConfigured();
    }

    private Resultado enviar(Envio envio) {
        try {
            if (envio.canal() == CanalComunicacao.EMAIL) {
                email.send(envio.destinatario(), envio.assunto(), envio.mensagem());
                return concluido(StatusComunicacao.SUCESSO, null, null);
            }
            String id = envio.instancia() == null ? whatsapp.send(envio.destinatario(), envio.mensagem())
                    : whatsapp.sendFrom(envio.instancia(), envio.destinatario(), envio.mensagem());
            return concluido(StatusComunicacao.SUCESSO, id, null);
        } catch (ResponseStatusException ex) {
            if (envio.canal() == CanalComunicacao.WHATSAPP && ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return concluido(StatusComunicacao.FALHA, null, "Destinatário inválido antes do envio.");
            }
            return incerto();
        } catch (RuntimeException ex) {
            return incerto();
        }
    }

    private void sinalizarExpiracao(Long id, LocalDateTime limite) {
        transacao.executeWithoutResult(tx -> comunicacoes.bloquearTentativa(id)
                .filter(c -> c.getEstadoDespacho() == EstadoDespacho.EM_ENVIO)
                .filter(c -> !c.getDespachoIniciadoEm().isAfter(limite))
                .ifPresent(c -> registrarResultado(c, new Resultado(StatusComunicacao.SEM_RESPOSTA,
                        EstadoDespacho.INCERTO_EXECUCAO, null, "Prazo de acompanhamento excedido. "
                                + "O encerramento da execução ainda não foi confirmado."), null)));
    }

    @Transactional
    public void reconciliar(Long processoId, Long tentativaId, ReconciliarComunicacaoRequest request) {
        var operador = auditoria.exigirOperador();
        var processo = processos.bloquearPorId(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        acesso.conferirProcesso(operador, processo);
        auditoria.exigirOperadorGlobal();
        var tentativa = comunicacoes.bloquearTentativa(tentativaId)
                .filter(c -> c.getProcesso().getId().equals(processoId))
                .orElseThrow(() -> new ResourceNotFoundException("Tentativa não encontrada."));
        if (tentativa.getEstadoDespacho() == EstadoDespacho.INCERTO_EXECUCAO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O encerramento da execução original ainda não foi confirmado.");
        }
        if (tentativa.getEstadoDespacho() != EstadoDespacho.INCERTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A tentativa não está aguardando reconciliação.");
        }
        if (request.resultado() != ReconciliarComunicacaoRequest.Resultado.ACEITO_PROVEDOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O provedor ainda pode concluir o envio. Só é possível registrar um aceite verificado.");
        }
        registrarResultado(tentativa, concluido(StatusComunicacao.SUCESSO, null, null),
                request.evidencia().trim(), operador);
        auditoria.registrar("Reconciliação de comunicação", "Processo", processoId,
                "Tentativa #" + tentativaId + " reconciliada como " + request.resultado() + ".");
    }

    private void registrarResultado(Comunicacao c, Resultado resultado, String evidencia) {
        registrarResultado(c, resultado, evidencia, c.getResponsavel());
    }

    private void registrarResultado(Comunicacao c, Resultado resultado, String evidencia,
            com.systemX.senai.entity.Usuario operador) {
        c.setEstadoDespacho(resultado.estado());
        c.setStatus(resultado.status());
        c.setErro(resultado.detalhe());
        c.setProvedorMensagemId(resultado.idProvedor());
        if (resultado.status().aceito() && evidencia == null) c.setEnviadaEm(agora());
        historico.save(HistoricoComunicacao.builder().comunicacao(c).canal(c.getCanal())
                .status(c.getStatus()).usuario(operador).data(agora())
                .resposta(resultado.detalhe()).observacao(evidencia).build());
    }

    private static Resultado concluido(StatusComunicacao status, String id, String detalhe) {
        return new Resultado(status, EstadoDespacho.CONCLUIDO, id, detalhe);
    }

    private static Resultado incerto() {
        return new Resultado(StatusComunicacao.SEM_RESPOSTA, EstadoDespacho.INCERTO, null,
                "Resultado não confirmado. Verifique o provedor antes de reconciliar.");
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static void exigirForaDeTransacao() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Despacho externo exige execução fora de transação.");
        }
    }
}
