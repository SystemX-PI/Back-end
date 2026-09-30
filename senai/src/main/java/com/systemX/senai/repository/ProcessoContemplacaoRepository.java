package com.systemX.senai.repository;
import com.systemX.senai.entity.ProcessoContemplacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
public interface ProcessoContemplacaoRepository extends JpaRepository<ProcessoContemplacao, Long> {
	Optional<ProcessoContemplacao> findByOrigemAndIdExterno(String origem, String idExterno);
	List<ProcessoContemplacao> findAllByOrderByCriadoEmDesc();
}
