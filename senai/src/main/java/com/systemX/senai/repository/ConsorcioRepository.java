package com.systemX.senai.repository;
import com.systemX.senai.entity.Consorcio;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ConsorcioRepository extends JpaRepository<Consorcio, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Consorcio> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "tipo")
    org.springframework.data.domain.Page<Consorcio> findAll(org.springframework.data.jpa.domain.Specification<Consorcio> spec, org.springframework.data.domain.Pageable pageable);
}