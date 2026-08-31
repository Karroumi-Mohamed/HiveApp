package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyEffect;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.service.impl.CommercialPolicyAdminServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommercialPolicyPrecedenceTest {

    @Test
    void directTargetsOutrankBroadAudiencesAndRestrictionsOutrankGrants() {
        assertThat(CommercialPolicyPrecedence.targetSpecificity(CommercialPolicyTargetKind.ACCOUNT))
                .isGreaterThan(CommercialPolicyPrecedence.targetSpecificity(
                        CommercialPolicyTargetKind.SEGMENT));
        assertThat(CommercialPolicyPrecedence.targetSpecificity(CommercialPolicyTargetKind.ACCOUNT_SET))
                .isEqualTo(CommercialPolicyPrecedence.targetSpecificity(
                        CommercialPolicyTargetKind.ACCOUNT));
        assertThat(CommercialPolicyPrecedence.targetSpecificity(
                        CommercialPolicyTargetKind.PLAN_REVISION_SUBSCRIBERS))
                .isEqualTo(CommercialPolicyPrecedence.targetSpecificity(
                        CommercialPolicyTargetKind.SEGMENT));
        assertThat(CommercialPolicyPrecedence.effectClass(
                        CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION))
                .isGreaterThan(CommercialPolicyPrecedence.effectClass(
                        CommercialPolicyEffectType.ALLOW_PRODUCT_SELECTION));
    }

    @Test
    void activationAndResumeUseOneRepeatableDatabaseAudienceSnapshot() throws Exception {
        for (String method : java.util.List.of("activate", "resume")) {
            Transactional transaction = CommercialPolicyAdminServiceImpl.class
                    .getMethod(method, UUID.class, CommercialPolicyRequests.Activation.class)
                    .getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        }
        Transactional preview = CommercialPolicyAdminServiceImpl.class
                .getMethod("previewActivation", UUID.class)
                .getAnnotation(Transactional.class);
        assertThat(preview).isNotNull();
        assertThat(preview.readOnly()).isTrue();
        assertThat(preview.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void comparatorAppliesSpecificityThenPriorityThenRestrictionAndStableIdentity() {
        CommercialPolicy broad = policy(
                CommercialPolicyTargetKind.SEGMENT, 100, UUID.fromString(
                        "00000000-0000-0000-0000-000000000001"), 1,
                UUID.fromString("00000000-0000-0000-0000-000000000011"));
        CommercialPolicy direct = policy(
                CommercialPolicyTargetKind.ACCOUNT, 10, UUID.fromString(
                        "00000000-0000-0000-0000-000000000002"), 1,
                UUID.fromString("00000000-0000-0000-0000-000000000022"));
        CommercialPolicyEffect broadRestriction = effect(
                CommercialPolicyEffectType.BLOCK_FEATURE, 0);
        CommercialPolicyEffect directGrant = effect(
                CommercialPolicyEffectType.GRANT_ADD_ON, 1);
        CommercialPolicyEffect directRestriction = effect(
                CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION, 2);

        List<CommercialPolicyPrecedence.Candidate> ordered = List.of(
                        new CommercialPolicyPrecedence.Candidate(broad, broadRestriction),
                        new CommercialPolicyPrecedence.Candidate(direct, directGrant),
                        new CommercialPolicyPrecedence.Candidate(direct, directRestriction))
                .stream().sorted(CommercialPolicyPrecedence.deterministicOrder()).toList();

        assertThat(ordered).extracting(candidate -> candidate.effect().getType())
                .containsExactly(
                        CommercialPolicyEffectType.BLOCK_PRODUCT_SELECTION,
                        CommercialPolicyEffectType.GRANT_ADD_ON,
                        CommercialPolicyEffectType.BLOCK_FEATURE);
    }

    private CommercialPolicy policy(
            CommercialPolicyTargetKind kind,
            int priority,
            UUID lineageId,
            int revision,
            UUID id
    ) {
        CommercialPolicy policy = mock(CommercialPolicy.class);
        when(policy.getTargetKind()).thenReturn(kind);
        when(policy.getPriority()).thenReturn(priority);
        when(policy.getLineageId()).thenReturn(lineageId);
        when(policy.getRevisionNumber()).thenReturn(revision);
        when(policy.getId()).thenReturn(id);
        return policy;
    }

    private CommercialPolicyEffect effect(CommercialPolicyEffectType type, int order) {
        CommercialPolicyEffect effect = mock(CommercialPolicyEffect.class);
        when(effect.getType()).thenReturn(type);
        when(effect.getEffectOrder()).thenReturn(order);
        return effect;
    }
}
