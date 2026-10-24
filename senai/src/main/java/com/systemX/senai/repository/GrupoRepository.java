package com.systemX.senai.repository;
import com.systemX.senai.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GrupoRepository extends JpaRepository<Grupo, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Grupo> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "consorcio")
    org.springframework.data.domain.Page<Grupo> findAll(org.springframework.data.jpa.domain.Specification<Grupo> spec, org.springframework.data.domain.Pageable pageable);
}