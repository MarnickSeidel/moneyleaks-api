package com.marnickseidel.moneyleaks.repository;

import com.marnickseidel.moneyleaks.model.entity.Subscription;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
