package com.systemX.senai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.systemX.senai.entity.PlatformMailbox;
public interface PlatformMailboxRepository extends JpaRepository<PlatformMailbox, Long> {
 java.util.List<PlatformMailbox> findByContaIdOrderByIdAsc(Long contaId);
 java.util.Optional<PlatformMailbox> findByIdAndContaId(Long id, Long contaId);
 long countByContaId(Long contaId);
}
