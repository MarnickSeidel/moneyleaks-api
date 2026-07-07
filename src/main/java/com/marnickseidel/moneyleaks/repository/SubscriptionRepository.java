package com.marnickseidel.moneyleaks.repository;

import com.marnickseidel.moneyleaks.model.entity.Subscription;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByActiveTrueOrderByAmountDesc();

    Optional<Subscription> findByMerchantNormalizedAndAmountAndIntervalType(
            String merchantNormalized,
            BigDecimal amount,
            SubscriptionInterval intervalType
    );

    @Modifying
    @Query("update Subscription s set s.active = false where s.active = true")
    void deactivateAll();
}
