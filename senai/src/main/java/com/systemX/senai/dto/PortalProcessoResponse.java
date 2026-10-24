package com.systemX.senai.dto;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record PortalProcessoResponse(ProcessoResponse processo, String responsavel, String cota,
        BigDecimal valorCredito, List<DocumentoItem> documentos, List<HistoricoItem> historico) {
    public record DocumentoItem(Long id, String tipo, String status, String nomeArquivo, String observacao) {}
    public record HistoricoItem(Long id, String statusAnterior, String novoStatus, LocalDateTime data, String observacao) {}
}
