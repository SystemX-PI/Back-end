package com.systemX.senai.repository;
import com.systemX.senai.entity.Comunicacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ComunicacaoRepository extends JpaRepository<Comunicacao, Long> {
	Optional<Comunicacao> findByProcessoId(Long processoId);
}