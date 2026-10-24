package com.systemX.senai.repository;
import com.systemX.senai.entity.HistoricoProcesso;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HistoricoProcessoRepository extends JpaRepository<HistoricoProcesso, Long> {    java.util.List<HistoricoProcesso> findByProcessoId(Long processoId, org.springframework.data.domain.Pageable pageable);
}