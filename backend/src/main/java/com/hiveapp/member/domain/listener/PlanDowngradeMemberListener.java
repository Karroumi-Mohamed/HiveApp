package com.hiveapp.member.domain.listener;

import com.hiveapp.member.domain.entity.Member;
import com.hiveapp.member.domain.repository.MemberRepository;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.service.PlanService;
import com.hiveapp.plan.event.PlanChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * On plan downgrade, deactivates excess members if the new plan's maxMembers
 * is exceeded. Never deactivates the account owner.
 *
 * Spec rule: "Lors d'un downgrade, les Members excédentaires sont désactivés"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanDowngradeMemberListener {

    private final MemberRepository memberRepository;
    private final PlanService planService;

    @EventListener
    @Transactional
    public void onPlanChanged(PlanChangedEvent event) {
        UUID accountId = event.getAccountId();
        UUID newPlanId = event.getNewPlanId();

        Plan newPlan = planService.findPlanOrThrow(newPlanId);
        int maxMembers = newPlan.getMaxMembers();

        List<Member> activeMembers = memberRepository.findByAccountIdAndIsActiveTrue(accountId);

        if (activeMembers.size() <= maxMembers) {
            return;
        }

        int excess = activeMembers.size() - maxMembers;

        // Sort: owner first (kept), then by joinedAt ascending (oldest kept)
        // We deactivate the most recently joined non-owner members
        List<Member> deactivationCandidates = activeMembers.stream()
                .filter(m -> !m.isOwner())
                .sorted(Comparator.comparing(Member::getJoinedAt).reversed())
                .limit(excess)
                .toList();

        for (Member member : deactivationCandidates) {
            member.deactivate();
            memberRepository.save(member);
            log.info("Member '{}' deactivated due to plan downgrade (account={}, maxMembers={})",
                    member.getDisplayName(), accountId, maxMembers);
        }

        log.info("Deactivated {} excess members for account {} after plan downgrade",
                deactivationCandidates.size(), accountId);
    }
}
