package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.model.dto.SubscriptionResponse;
import com.marnickseidel.moneyleaks.model.dto.SubscriptionSummaryResponse;
import com.marnickseidel.moneyleaks.model.entity.Subscription;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;
import com.marnickseidel.moneyleaks.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listActive() {
        return subscriptionRepository.findByActiveTrueOrderByAmountDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionSummaryResponse summary() {
        List<Subscription> active = subscriptionRepository.findByActiveTrueOrderByAmountDesc();

        BigDecimal monthlyTotal = active.stream()
                .map(this::monthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new SubscriptionSummaryResponse(active.size(), monthlyTotal, "EUR");
    }

    private BigDecimal monthlyAmount(Subscription subscription) {
        if (subscription.getIntervalType() == SubscriptionInterval.YEARLY) {
            return subscription.getAmount()
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
        return subscription.getAmount();
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getMerchantNormalized(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getIntervalType(),
                subscription.getOccurrenceCount(),
                subscription.getFirstSeen(),
                subscription.getLastSeen(),
                subscription.getConfidence()
        );
    }
}
