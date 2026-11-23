package com.systemX.senai.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.systemX.senai.dto.PaginaResponse;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;
import com.systemX.senai.exception.ResourceNotFoundException;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.systemX.senai.dto.NotificationHistoryResponse;
import com.systemX.senai.entity.Comunicacao;
import com.systemX.senai.enums.CanalComunicacao;
import com.systemX.senai.enums.StatusComunicacao;
import com.systemX.senai.repository.ComunicacaoRepository;

@Service
public class NotificationHistoryService {
    private static final int RECENT_LIMIT = 20;
    private final ComunicacaoRepository communications;

    private final ProcessoContemplacaoRepository processes;
    private final OperacaoAcesso acesso;
    private final AuditoriaService auditoria;

    public NotificationHistoryService(ComunicacaoRepository communications,
            ProcessoContemplacaoRepository processes, OperacaoAcesso acesso, AuditoriaService auditoria) {
        this.communications = communications;
        this.processes = processes;
        this.acesso = acesso;
        this.auditoria = auditoria;
    }

    public record ProcessItem(Long id, CanalComunicacao canal, StatusComunicacao status,
            String destinatario, LocalDateTime criadaEm, LocalDateTime enviadaEm, String erro,
            Long comunicadoOriginalId, com.systemX.senai.enums.EstadoDespacho estadoDespacho,
            boolean reconciliavel) {}

    @Transactional(readOnly = true)
    public PaginaResponse<ProcessItem> processHistory(Long processId, int page, int size) {
        var pagination = Paginacao.pagina(page, size);
        var operador = auditoria.exigirOperador();
        boolean podeReconciliar = operador.getContaId() == null
                || operador.getTipo() == com.systemX.senai.enums.TipoUsuario.ADMINISTRADOR;
        acesso.conferirProcesso(operador, processes.findById(processId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processId)));
        return PaginaResponse.from(communications.findByProcessoId(processId, pagination).map(c ->
                new ProcessItem(c.getId(), c.getCanal(), c.getStatus(), c.getDestinatario(),
                        c.getCriadaEm(), c.getEnviadaEm(), publicError(c),
                        c.getComunicadoOriginal() == null ? null : c.getComunicadoOriginal().getId(), c.getEstadoDespacho(),
                        podeReconciliar && c.getEstadoDespacho() == com.systemX.senai.enums.EstadoDespacho.INCERTO)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NotificationHistoryResponse operationalHistory(LocalDate start, LocalDate end, CanalComunicacao channel) {
        return history(auditoria.exigirOperador().getContaId(), start, end, channel);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NotificationHistoryResponse history(Long accountId, LocalDate start, LocalDate end,
            CanalComunicacao channel) {
        if (start == null || end == null || end.isBefore(start)
                || start.getYear() < 1000 || end.getYear() > 9999
                || end.equals(LocalDate.of(9999, 12, 31))
                || ChronoUnit.DAYS.between(start, end) >= 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe um período válido de até 366 dias, incluindo início e fim.");
        }
        var from = start.atStartOfDay();
        var until = end.plusDays(1).atStartOfDay();
        long total = 0;
        long accepted = 0;
        long failed = 0;
        for (var count : communications.countNotificationStatuses(accountId, from, until, channel)) {
            total += count.getTotal();
            if (count.getStatus().aceito()) {
                accepted += count.getTotal();
            } else if (count.getStatus() == StatusComunicacao.FALHA) {
                failed += count.getTotal();
            }
        }
        var items = communications.findRecentNotifications(accountId, from, until, channel,
                PageRequest.of(0, RECENT_LIMIT)).stream().map(c -> new NotificationHistoryResponse.Item(
                    c.getId(), c.getProcesso().getId(), c.getProcesso().getIdExterno(), c.getCanal().name(),
                    c.getStatus().name(), c.getDestinatario(), c.getCriadaEm(), c.getEnviadaEm(), publicError(c)))
                .toList();
        return new NotificationHistoryResponse(start, end, channel == null ? "TODOS" : channel.name(),
                total, accepted, total - accepted - failed, failed, items);
    }

    static String publicError(Comunicacao communication) {
        if (communication.getStatus() == StatusComunicacao.SEM_RESPOSTA) {
            return "O provedor não confirmou o envio. Confira o canal antes de reenviar.";
        }
        if (communication.getStatus() == StatusComunicacao.FALHA) {
            return "Não foi possível enviar a notificação. Consulte o administrador.";
        }
        if (communication.getErro() != null && !communication.getErro().isBlank()) {
            return "Envio pendente. Consulte o administrador.";
        }
        return null;
    }
}
