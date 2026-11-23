package com.systemX.senai.repository;
import com.systemX.senai.entity.ProcessoContemplacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
public interface ProcessoContemplacaoRepository extends JpaRepository<ProcessoContemplacao, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessoContemplacao> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcessoContemplacao p where p.id = :id")
    Optional<ProcessoContemplacao> bloquearPorId(@Param("id") Long id);

	Optional<ProcessoContemplacao> findByOrigemAndIdExterno(String origem, String idExterno);
	List<ProcessoContemplacao> findAllByOrderByCriadoEmDesc();
    org.springframework.data.domain.Page<ProcessoContemplacao> findByClienteEmail(String email, org.springframework.data.domain.Pageable pageable);
    Optional<ProcessoContemplacao> findByIdAndClienteEmail(Long id, String email);
}
