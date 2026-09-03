package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.ClientSubscriptionEntitlementState;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import java.math.BigDecimal;
import java.util.List;

/** Internal exact commercial selection used by the special-agreement finalizer. */
public record SpecialAgreementSelectionAssessment(
        Subscription current,
        Plan targetPlan,
        SubscriptionEntitlementSnapshot targetSnapshot,
        SubscriptionOverrides selection,
        BigDecimal catalogueCycleAmount,
        String currencyCode,
        ClientSubscriptionEntitlementState currentEntitlements,
        ClientSubscriptionEntitlementState targetEntitlements,
        List<SubscriptionChangeConflict> conflicts,
        boolean allowed,
        String selectionFingerprint) {}
