package com.systemX.senai.repository;
import com.systemX.senai.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from PasswordResetToken t where t.usuarioId=:id")
 Optional<PasswordResetToken> lockByUsuarioId(@Param("id") Long id);
 Optional<PasswordResetToken> findByUsuarioId(Long usuarioId);
}
