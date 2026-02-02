package com.hiveapp.member.domain.service;

import com.hiveapp.member.domain.dto.*;
import com.hiveapp.member.domain.entity.Member;
import com.hiveapp.member.domain.entity.MemberRole;
import com.hiveapp.member.domain.mapper.MemberMapper;
import com.hiveapp.member.domain.repository.MemberRepository;
import com.hiveapp.member.domain.repository.MemberRoleRepository;
import com.hiveapp.member.event.MemberRolesChangedEvent;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.service.PlanService;
import com.hiveapp.subscription.domain.entity.Subscription;
import com.hiveapp.subscription.domain.repository.SubscriptionRepository;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberMapper memberMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;

    @Transactional
    public MemberResponse createMember(CreateMemberRequest request) {
        UUID userId = request.getUserId();

        if (memberRepository.existsByUserIdAndAccountId(userId, request.getAccountId())) {
            throw new DuplicateResourceException("Member", "userId+accountId", userId);
        }

        Subscription subscription = subscriptionRepository
                .findByAccountIdAndStatus(request.getAccountId(), "active")
                .orElseThrow(() -> new BusinessException("No active subscription found for account"));

        Plan plan = planService.findPlanOrThrow(subscription.getPlanId());

        long currentCount = memberRepository.countByAccountIdAndIsActiveTrue(request.getAccountId());
        if (currentCount >= plan.getMaxMembers()) {
            throw new BusinessException(
                    "Maximum number of members (" + plan.getMaxMembers() + ") reached for current plan");
        }

        String displayName = request.getDisplayName() != null
                ? request.getDisplayName()
                : request.getFirstName() + " " + request.getLastName();

        Member member = Member.builder()
                .userId(userId)
                .accountId(request.getAccountId())
                .displayName(displayName)
                .build();

        Member saved = memberRepository.save(member);
        log.info("Member created: {} in account {}", saved.getId(), saved.getAccountId());
        return memberMapper.toResponse(saved);
    }

    @Transactional
    public MemberResponse createOwnerMember(UUID userId, UUID accountId, String displayName) {
        Member member = Member.builder()
                .userId(userId)
                .accountId(accountId)
                .displayName(displayName)
                .isOwner(true)
                .build();

        Member saved = memberRepository.save(member);
        log.info("Owner member created: {} in account {}", saved.getId(), accountId);
        return memberMapper.toResponse(saved);
    }

    @Transactional
    public void assignRole(UUID memberId, AssignRoleRequest request) {
        Member member = memberRepository.findByIdWithRoles(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        MemberRole memberRole = MemberRole.builder()
                .roleId(request.getRoleId())
                .companyId(request.getCompanyId())
                .build();

        member.addRole(memberRole);
        memberRepository.save(member);

        eventPublisher.publishEvent(new MemberRolesChangedEvent(memberId, member.getAccountId()));
        log.info("Role {} assigned to member {} (company: {})", request.getRoleId(), memberId, request.getCompanyId());
    }

    @Transactional
    public void removeRole(UUID memberId, UUID roleId, UUID companyId) {
        memberRoleRepository.deleteByMemberIdAndRoleIdAndCompanyId(memberId, roleId, companyId);

        Member member = findMemberOrThrow(memberId);
        eventPublisher.publishEvent(new MemberRolesChangedEvent(memberId, member.getAccountId()));
        log.info("Role {} removed from member {} (company: {})", roleId, memberId, companyId);
    }

    @Transactional
    public void deactivateMember(UUID id) {
        Member member = findMemberOrThrow(id);
        if (member.isOwner()) {
            throw new BusinessException("Cannot deactivate account owner");
        }
        member.deactivate();
        memberRepository.save(member);
        log.info("Member deactivated: {}", id);
    }

    public MemberResponse getMemberById(UUID id) {
        Member member = memberRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
        return memberMapper.toResponse(member);
    }

    public List<MemberResponse> getMembersByAccountId(UUID accountId) {
        return memberMapper.toResponseList(memberRepository.findByAccountIdAndIsActiveTrue(accountId));
    }

    public MemberResponse getMemberByUserAndAccount(UUID userId, UUID accountId) {
        Member member = memberRepository.findByUserIdAndAccountIdWithRoles(userId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId+accountId", userId));
        return memberMapper.toResponse(member);
    }

    public Set<UUID> getRoleIdsForMemberAndCompany(UUID memberId, UUID companyId) {
        return memberRoleRepository.findRoleIdsForMemberAndCompany(memberId, companyId);
    }

    public Set<UUID> getAllRoleIds(UUID memberId) {
        return memberRoleRepository.findAllRoleIdsByMemberId(memberId);
    }

    public Set<UUID> getAccessibleCompanyIds(UUID memberId) {
        return memberRoleRepository.findAccessibleCompanyIdsByMemberId(memberId);
    }

    public Member findMemberOrThrow(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
    }
}
