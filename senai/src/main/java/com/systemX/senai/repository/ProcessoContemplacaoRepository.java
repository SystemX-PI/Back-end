package com.systemX.senai.repository;
import com.systemX.senai.entity.ProcessoContemplacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
public interface ProcessoContemplacaoRepository extends JpaRepository<ProcessoContemplacao, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProcessoContemplacao> {
	Optional<ProcessoContemplacao> findByOrigemAndIdExterno(String origem, String idExterno);
	List<ProcessoContemplacao> findAllByOrderByCriadoEmDesc();
    org.springframework.data.domain.Page<ProcessoContemplacao> findByClienteEmail(String email, org.springframework.data.domain.Pageable pageable);
    Optional<ProcessoContemplacao> findByIdAndClienteEmail(Long id, String email);
}
