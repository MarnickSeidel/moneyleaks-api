package com.marnickseidel.moneyleaks.controller;

import com.marnickseidel.moneyleaks.model.dto.SubscriptionResponse;
import com.marnickseidel.moneyleaks.model.dto.SubscriptionSummaryResponse;
import com.marnickseidel.moneyleaks.service.SubscriptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public List<SubscriptionResponse> list() {
        return subscriptionService.listActive();
    }

    @GetMapping("/summary")
    public SubscriptionSummaryResponse summary() {
        return subscriptionService.summary();
    }
}
