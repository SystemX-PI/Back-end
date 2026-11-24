package com.systemX.senai.service;
import com.systemX.senai.entity.*;
import com.systemX.senai.enums.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;

final class FilaFiltro {
    private FilaFiltro() {}
    static Specification<ProcessoContemplacao> criar(String q, String status) {
        if (!java.util.Set.of("", "FALHA", "PENDENTE", "COMUNICADO_PARCIAL", "COMUNICADO_NOS_DOIS").contains(status))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Status invalido.");
        return (root, query, cb) -> {
            Predicate email = canal(root, query, cb, CanalComunicacao.EMAIL, StatusComunicacao.ACEITOS, null);
            Predicate whats = canal(root, query, cb, CanalComunicacao.WHATSAPP, StatusComunicacao.ACEITOS, null);
            Predicate filtro = switch (status) {
                case "FALHA" -> canal(root, query, cb, null, java.util.Set.of(StatusComunicacao.FALHA), null);
                case "PENDENTE" -> cb.not(cb.or(email, whats));
                case "COMUNICADO_PARCIAL" -> cb.or(cb.and(email, cb.not(whats)), cb.and(whats, cb.not(email)));
                case "COMUNICADO_NOS_DOIS" -> cb.and(email, whats);
                default -> cb.conjunction();
            };
            Predicate termo = cb.conjunction();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(java.util.Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                termo = cb.or(cb.like(cb.lower(root.get("origem")), like, '\\'),
                    cb.like(cb.lower(root.get("idExterno")), like, '\\'),
                    cb.like(cb.lower(root.join("cliente", JoinType.LEFT).get("nome")), like, '\\'),
                    canal(root, query, cb, null, null, like));
                try { termo = cb.or(termo, cb.equal(root.get("id"), Long.valueOf(q.trim()))); }
                catch (NumberFormatException ignored) {
                    // The search term is not a numeric process identifier; text filters still apply.
                }
            }
            return cb.and(canal(root, query, cb, null, null, null), filtro, termo);
        };
    }
    private static Predicate canal(Root<ProcessoContemplacao> p, CriteriaQuery<?> query, CriteriaBuilder cb,
            CanalComunicacao canal, java.util.Set<StatusComunicacao> status, String destinatario) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Comunicacao> c = sub.from(Comunicacao.class);
        Subquery<Long> ultima = sub.subquery(Long.class);
        Root<Comunicacao> c2 = ultima.from(Comunicacao.class);
        ultima.select(cb.max(c2.<Long>get("id"))).where(cb.equal(c2.get("processo"), p), cb.equal(c2.get("canal"), c.get("canal")));
        java.util.List<Predicate> filters = new java.util.ArrayList<>();
        filters.add(cb.equal(c.get("processo"), p)); filters.add(cb.equal(c.get("id"), ultima));
        if (canal != null) filters.add(cb.equal(c.get("canal"), canal));
        if (status != null) filters.add(c.get("status").in(status));
        if (destinatario != null) filters.add(cb.like(cb.lower(c.get("destinatario")), destinatario, '\\'));
        sub.select(c.get("id")).where(filters.toArray(Predicate[]::new));
        return cb.exists(sub);
    }
}
