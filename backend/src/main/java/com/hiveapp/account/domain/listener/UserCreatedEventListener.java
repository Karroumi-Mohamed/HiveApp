package com.hiveapp.account.domain.listener;

import com.hiveapp.account.domain.dto.CreateAccountRequest;
import com.hiveapp.account.domain.service.AccountService;
import com.hiveapp.identity.event.UserCreatedEvent;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.repository.PlanRepository;
import com.hiveapp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for UserCreatedEvent (fired on registration) and creates the user's Account.
 * This bridges the Identity module → Account module without direct coupling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventListener {

    private static final String DEFAULT_PLAN_CODE = "FREE";

    private final AccountService accountService;
    private final PlanRepository planRepository;

    @EventListener
    public void onUserCreated(UserCreatedEvent event) {
        log.info("Handling UserCreatedEvent for user {} ({})", event.getUserId(), event.getEmail());

        // Resolve the default plan — every new account starts on the FREE plan
        Plan defaultPlan = planRepository.findByCode(DEFAULT_PLAN_CODE)
                .orElseThrow(() -> new BusinessException(
                        "Default plan '" + DEFAULT_PLAN_CODE + "' not found. Seed data required."));

        String accountName = event.getAccountName() != null
                ? event.getAccountName()
                : event.getEmail().split("@")[0] + "'s Account";

        CreateAccountRequest request = CreateAccountRequest.builder()
                .name(accountName)
                .planId(defaultPlan.getId())
                .build();

        accountService.createAccount(event.getUserId(), request);
        log.info("Account '{}' created for user {} with plan '{}'",
                accountName, event.getUserId(), defaultPlan.getCode());
    }
}
