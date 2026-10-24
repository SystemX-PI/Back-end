package com.systemX.senai.service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
public final class Paginacao {
    private Paginacao() {}
    public static PageRequest pagina(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Pagina deve ser >= 0 e tamanho entre 1 e 100.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
    public static <T> Specification<T> busca(String q, String... fields) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            String term = "%" + q.trim().toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return cb.or(java.util.Arrays.stream(fields).map(field -> cb.like(cb.lower(root.get(field)), term, '\\')).toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
