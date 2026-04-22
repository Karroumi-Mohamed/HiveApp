package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.service.WorkspaceProvisioningService;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceProvisioningServiceImpl implements WorkspaceProvisioningService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional
    public void provision(UUID userId, String email) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Derive slug from email prefix; append random suffix on collision
        String baseSlug = email.substring(0, email.indexOf('@'))
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        String slug = resolveUniqueSlug(baseSlug);

        Account account = new Account();
        account.setOwnerId(userId);
        account.setName(user.getFirstName() + "'s Workspace");
        account.setSlug(slug);
        account.setActive(true);
        account = accountRepository.save(account);

        Member member = new Member();
        member.setAccount(account);
        member.setUser(user);
        member.setDisplayName(user.getFirstName());
        member.setOwner(true);
        member.setActive(true);
        memberRepository.save(member);

        provisionFreeSubscription(account);

        log.info("Workspace provisioned for user={} account={} slug={}", userId, account.getId(), slug);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String resolveUniqueSlug(String base) {
        if (base.isBlank()) base = "workspace";
        if (!accountRepository.existsBySlug(base)) return base;
        // Collision — append short random hex
        String candidate;
        do {
            candidate = base + "-" + Integer.toHexString((int) (Math.random() * 0xFFFF));
        } while (accountRepository.existsBySlug(candidate));
        return candidate;
    }

    private void provisionFreeSubscription(Account account) {
        var freePlan = planRepository.findByCode("FREE").orElse(null);
        if (freePlan == null) {
            log.warn("FREE plan not found — skipping subscription provisioning for account={}. " +
                     "Run PlanSeeder or create the FREE plan manually.", account.getId());
            return;
        }
        Subscription sub = new Subscription();
        sub.setAccount(account);
        sub.setPlan(freePlan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPrice(freePlan.getPrice());
        subscriptionRepository.save(sub);
        log.info("FREE subscription provisioned for account={}", account.getId());
    }
}
