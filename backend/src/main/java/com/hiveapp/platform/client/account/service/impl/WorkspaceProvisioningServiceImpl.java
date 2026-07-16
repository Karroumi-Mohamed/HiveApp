package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.WorkspaceProvisioningResult;
import com.hiveapp.platform.client.account.service.WorkspaceProvisioningService;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceProvisioningServiceImpl implements WorkspaceProvisioningService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    @Override
    @Transactional
    public WorkspaceProvisioningResult provision(UUID userId, String email) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        var existingAccount = accountRepository.findByOwner_Id(userId);
        if (existingAccount.isPresent()) {
            return existingProvisioning(existingAccount.orElseThrow(), userId);
        }

        Plan freePlan = requireUsableFreePlan();
        String slug = initialSlug(email, userId);

        Account account = new Account();
        account.setOwner(user);
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

        provisionFreeSubscription(account, freePlan);

        log.info("Workspace provisioned for user={} account={} slug={}", userId, account.getId(), slug);
        return new WorkspaceProvisioningResult(account.getId(), slug, true);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String initialSlug(String email, UUID userId) {
        int separator = email.indexOf('@');
        String prefix = separator > 0 ? email.substring(0, separator) : email;
        String base = prefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.isBlank()) {
            base = "workspace";
        }
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        return base + "-" + userId.toString().replace("-", "");
    }

    private Plan requireUsableFreePlan() {
        Plan freePlan = planRepository.findByCode("FREE")
                .orElseThrow(() -> new InvalidStateException(
                        "Workspace registration is unavailable because the required FREE plan is not configured."));
        if (!freePlan.isActive()) {
            throw new InvalidStateException(
                    "Workspace registration is unavailable because the required FREE plan is inactive.");
        }
        return freePlan;
    }

    private void provisionFreeSubscription(Account account, Plan freePlan) {
        Subscription sub = new Subscription();
        sub.setAccount(account);
        sub.setPlan(freePlan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCustomOverrides(subscriptionOverrideReader.write(SubscriptionOverrides.empty()));
        sub.setEntitlementSnapshot(subscriptionSnapshotReader.write(subscriptionSnapshotFactory.fromPlan(freePlan)));
        sub.setCurrentPrice(freePlan.getPrice());
        subscriptionRepository.saveAndFlush(sub);
        log.info("FREE subscription provisioned for account={}", account.getId());
    }

    private WorkspaceProvisioningResult existingProvisioning(Account account, UUID userId) {
        Member ownerMember = memberRepository.findByAccountIdAndUserId(account.getId(), userId)
                .orElseThrow(() -> incompleteProvisioning(account));
        if (!ownerMember.isOwner()) {
            throw incompleteProvisioning(account);
        }

        List<Subscription> usableSubscriptions = subscriptionRepository.findAllByAccountIdAndStatusIn(
                account.getId(), List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING));
        if (usableSubscriptions.size() != 1
                || usableSubscriptions.getFirst().getEntitlementSnapshot() == null
                || usableSubscriptions.getFirst().getEntitlementSnapshot().isBlank()) {
            throw incompleteProvisioning(account);
        }

        return new WorkspaceProvisioningResult(account.getId(), account.getSlug(), false);
    }

    private InvalidStateException incompleteProvisioning(Account account) {
        return new InvalidStateException(
                "Existing workspace provisioning is incomplete for account " + account.getId() + ".");
    }
}
