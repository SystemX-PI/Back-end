package com.systemX.senai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.systemX.senai.entity.PlatformAccount;
public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
 java.util.Optional<PlatformAccount> findByStripeCustomerId(String stripeCustomerId);
 java.util.Optional<PlatformAccount> findByStripeSubscriptionId(String stripeSubscriptionId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select a from PlatformAccount a where a.id=:id")
 java.util.Optional<PlatformAccount> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
