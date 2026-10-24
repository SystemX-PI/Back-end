package com.systemX.senai.dto;
import java.util.List;
import org.springframework.data.domain.Page;
public record PaginaResponse<T>(List<T> items, long total, int page, int size) {
    public static <T> PaginaResponse<T> from(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
