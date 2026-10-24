package com.systemX.senai.repository;
import com.systemX.senai.entity.PlatformCheckout;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PlatformCheckoutRepository extends JpaRepository<PlatformCheckout,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from PlatformCheckout c where c.id=:id and c.contaId=:account")
 Optional<PlatformCheckout> lockOwned(@Param("id") String id,@Param("account") Long account);
 Optional<PlatformCheckout> findFirstByContaIdAndPaymentMethodAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(Long contaId,String paymentMethod,String status,java.time.Instant now);
}
