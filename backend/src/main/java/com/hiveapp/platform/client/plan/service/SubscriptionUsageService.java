package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.ClientSubscriptionFeature;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionUsageService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final CollaborationRepository collaborationRepository;

    public long currentUsage(UUID accountId, String featureCode, String resource) {
        if (WorkspaceFeature.CODE.equals(featureCode) && WorkspaceFeature.MEMBERS.equals(resource)) {
            return memberRepository.findAllByAccountId(accountId).size();
        }
        if (WorkspaceFeature.CODE.equals(featureCode) && WorkspaceFeature.COMPANIES.equals(resource)) {
            return companyRepository.findAllByAccountId(accountId).size();
        }
        return 0L;
    }

    public long featureUsage(UUID accountId, String featureCode) {
        if (CompanyFeature.CODE.equals(featureCode)) {
            return companyRepository.findAllByAccountId(accountId).size();
        }
        if (StaffFeature.CODE.equals(featureCode)) {
            return memberRepository.findAllByAccountId(accountId).stream()
                    .filter(member -> !member.isOwner())
                    .count();
        }
        if (WorkspaceRolesFeature.CODE.equals(featureCode)) {
            return roleRepository.findAllByAccountId(accountId).stream()
                    .filter(role -> !role.isSystemRole())
                    .count();
        }
        if (B2bFeature.CODE.equals(featureCode)) {
            long incoming = collaborationRepository.findAllByClientAccountId(accountId).stream()
                    .filter(this::isLiveCollaboration)
                    .count();
            long outgoing = collaborationRepository.findAllByProviderAccountId(accountId).stream()
                    .filter(this::isLiveCollaboration)
                    .count();
            return incoming + outgoing;
        }
        if (ClientSubscriptionFeature.CODE.equals(featureCode)) {
            return 1L;
        }
        return 0L;
    }

    private boolean isLiveCollaboration(com.hiveapp.platform.client.collaboration.domain.entity.Collaboration collaboration) {
        return collaboration.getStatus() == CollaborationStatus.PENDING
                || collaboration.getStatus() == CollaborationStatus.ACTIVE
                || collaboration.getStatus() == CollaborationStatus.SUSPENDED;
    }
}
