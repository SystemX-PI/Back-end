package com.systemX.senai.repository;
import com.systemX.senai.entity.Comunicacao;
import com.systemX.senai.enums.CanalComunicacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ComunicacaoRepository extends JpaRepository<Comunicacao, Long> {
	Optional<Comunicacao> findFirstByProcessoIdOrderByIdDesc(Long processoId);
	Optional<Comunicacao> findFirstByProcessoIdAndCanalOrderByIdDesc(Long processoId, CanalComunicacao canal);
}