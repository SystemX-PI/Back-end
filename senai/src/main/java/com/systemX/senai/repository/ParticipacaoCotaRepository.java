package com.systemX.senai.repository;
import com.systemX.senai.entity.ParticipacaoCota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ParticipacaoCotaRepository extends JpaRepository<ParticipacaoCota, Long> {
	Optional<ParticipacaoCota> findByUsuarioIdAndCotaId(Long usuarioId, Long cotaId);
}