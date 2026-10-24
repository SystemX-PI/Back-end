package com.systemX.senai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.systemX.senai.entity.PlatformInstance;
public interface PlatformInstanceRepository extends JpaRepository<PlatformInstance, Long> {
 java.util.List<PlatformInstance> findByContaIdOrderByIdAsc(Long contaId);
 java.util.Optional<PlatformInstance> findByIdAndContaId(Long id, Long contaId);
 long countByContaId(Long contaId);
}
