package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyEffect;

import java.util.Comparator;
import java.util.UUID;

/**
 * Stable policy ordering. Account and explicit-Account targets are direct; Plan and Segment
 * audiences are broad. Platform hard limits are deliberately outside this comparator and always
 * remain a final veto in the commercial resolver.
 */
public final class CommercialPolicyPrecedence {

    private CommercialPolicyPrecedence() {}

    public static int targetSpecificity(CommercialPolicyTargetKind kind) {
        return switch (kind) {
            case ACCOUNT, ACCOUNT_SET -> 2;
            case PLAN_REVISION_SUBSCRIBERS, SEGMENT -> 1;
        };
    }

    public static int effectClass(CommercialPolicyEffectType type) {
        if (type.isRestriction()) return 3;
        if (type.isGrant()) return 2;
        return 1;
    }

    public static Comparator<Candidate> deterministicOrder() {
        return Comparator.comparingInt((Candidate candidate) ->
                        targetSpecificity(candidate.policy().getTargetKind())).reversed()
                .thenComparing(Comparator.comparingInt(
                        (Candidate candidate) -> candidate.policy().getPriority()).reversed())
                .thenComparing(Comparator.comparingInt(
                        (Candidate candidate) -> effectClass(candidate.effect().getType())).reversed())
                .thenComparing(candidate -> candidate.policy().getLineageId())
                .thenComparingInt(candidate -> candidate.policy().getRevisionNumber())
                .thenComparing(candidate -> candidate.policy().getId(),
                        Comparator.nullsLast(UUID::compareTo))
                .thenComparingInt(candidate -> candidate.effect().getEffectOrder());
    }

    public record Candidate(CommercialPolicy policy, CommercialPolicyEffect effect) {}
}
