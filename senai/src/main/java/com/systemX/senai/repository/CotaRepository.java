package com.systemX.senai.repository;
import com.systemX.senai.entity.Cota;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CotaRepository extends JpaRepository<Cota, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Cota> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cota c where c.id = :id")
    java.util.Optional<Cota> bloquearPorId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "grupo")
    org.springframework.data.domain.Page<Cota> findAll(org.springframework.data.jpa.domain.Specification<Cota> spec, org.springframework.data.domain.Pageable pageable);
}