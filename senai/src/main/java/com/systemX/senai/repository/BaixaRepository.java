package com.systemX.senai.repository;
import com.systemX.senai.entity.Baixa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface BaixaRepository extends JpaRepository<Baixa, Long> {
	Optional<Baixa> findByProcessoId(Long processoId);
}