package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.service.CompanySubscriptionImpactContributor;
import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.service.StaffSubscriptionImpactContributor;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.StaffFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaOwnershipImpactContributorTest {

    @Mock private MemberRepository memberRepository;
    @Mock private CompanyRepository companyRepository;

    @Test
    void staffOwnsAndMeasuresTheMemberQuota() {
        UUID accountId = UUID.randomUUID();
        when(memberRepository.countByAccountIdAndIsActiveTrue(accountId)).thenReturn(7L);
        var contributor = new StaffSubscriptionImpactContributor(memberRepository);

        assertThat(contributor.featureCode()).isEqualTo(StaffFeature.CODE);
        assertThat(contributor.quotaUsage(accountId, StaffFeature.MEMBERS)).hasValue(7L);
        assertThat(contributor.quotaUsage(accountId, CompanyFeature.COMPANIES)).isEmpty();
        verify(memberRepository).countByAccountIdAndIsActiveTrue(accountId);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void companiesOwnsAndMeasuresTheCompanyQuota() {
        UUID accountId = UUID.randomUUID();
        when(companyRepository.countByAccountIdAndIsActiveTrue(accountId)).thenReturn(3L);
        var contributor = new CompanySubscriptionImpactContributor(companyRepository);

        assertThat(contributor.featureCode()).isEqualTo(CompanyFeature.CODE);
        assertThat(contributor.quotaUsage(accountId, CompanyFeature.COMPANIES)).hasValue(3L);
        assertThat(contributor.quotaUsage(accountId, StaffFeature.MEMBERS)).isEmpty();
        verify(companyRepository).countByAccountIdAndIsActiveTrue(accountId);
        verifyNoInteractions(memberRepository);
    }
}
