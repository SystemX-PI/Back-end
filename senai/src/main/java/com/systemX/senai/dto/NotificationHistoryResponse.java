package com.systemX.senai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record NotificationHistoryResponse(LocalDate startDate, LocalDate endDate, String channel,
        long total, long accepted, long pending, long failed, List<Item> items) {
    public record Item(Long id, Long processId, String externalId, String channel, String status,
            String recipient, LocalDateTime createdAt, LocalDateTime sentAt, String error) {}
}
