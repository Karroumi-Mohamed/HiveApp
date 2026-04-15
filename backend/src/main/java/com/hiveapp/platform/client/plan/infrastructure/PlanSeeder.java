package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.feature.PlatformFeature;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds plan templates and their feature compositions.
 *
 * Order 3 — after FeatureSeeder (1) and PermissionSeeder (2).
 * Feature rows are guaranteed to exist by the time this runs.
 *
 * All platform features are included in every plan (boolean access).
 * WORKSPACE feature carries quota limits that differ by tier.
 * ERP module features are seeded by their own module seeders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSeeder {

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final FeatureRepository featureRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    @Transactional
    public void seed() {
        if (planRepository.count() > 0) {
            log.info("Plans already seeded — skipping.");
            return;
        }

        log.info("Seeding plan templates...");

        var free = createPlan("FREE",       "Free Plan",        BigDecimal.ZERO,              BillingCycle.FOREVER);
        var pro  = createPlan("PRO",        "Pro Plan",         new BigDecimal("29.99"),       BillingCycle.MONTHLY);
        var ent  = createPlan("ENTERPRISE", "Enterprise Plan",  new BigDecimal("99.99"),       BillingCycle.MONTHLY);

        // --- WORKSPACE (with quota limits per tier) ---
        seedWorkspace(free, 3L,   1L,   null,   null);   // FREE:       3 members, 1 company, no bumping
        seedWorkspace(pro,  10L,  5L,   "2.00", "5.00"); // PRO:       10 members, 5 companies, bumpable
        seedWorkspace(ent,  null, null, null,   null);   // ENTERPRISE: unlimited

        // --- Boolean-access platform features (same for all tiers) ---
        for (var featureEnum : new PlatformFeature[]{
                PlatformFeature.COMPANY,
                PlatformFeature.STAFF,
                PlatformFeature.SUBSCRIPTION,
                PlatformFeature.B2B,
                PlatformFeature.RBAC
        }) {
            var feature = featureRepository.findByCode(featureEnum.code()).orElse(null);
            if (feature == null) {
                log.warn("Feature '{}' not found — skipping PlanFeature seed.", featureEnum.code());
                continue;
            }
            for (var plan : List.of(free, pro, ent)) {
                assign(plan, feature, null, List.of());
            }
        }

        log.info("Plan seeding complete — FREE / PRO / ENTERPRISE with all platform features.");
    }

    private void seedWorkspace(Plan plan, Long members, Long companies,
                                String memberPrice, String companyPrice) {
        featureRepository.findByCode(PlatformFeature.WORKSPACE.code()).ifPresentOrElse(feature -> {
            var memberEntry   = memberPrice  != null
                    ? new QuotaLimitEntry(PlatformFeature.MEMBERS,   members,   new BigDecimal(memberPrice))
                    : new QuotaLimitEntry(PlatformFeature.MEMBERS,   members);
            var companyEntry  = companyPrice != null
                    ? new QuotaLimitEntry(PlatformFeature.COMPANIES, companies, new BigDecimal(companyPrice))
                    : new QuotaLimitEntry(PlatformFeature.COMPANIES, companies);
            assign(plan, feature, null, List.of(memberEntry, companyEntry));
        }, () -> log.warn("Feature '{}' not found — workspace quota not seeded.",
                PlatformFeature.WORKSPACE.code()));
    }

    private void assign(Plan plan, Feature feature, BigDecimal addOnPrice,
                        List<QuotaLimitEntry> quotaConfigs) {
        var pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setAddOnPrice(addOnPrice);
        pf.setQuotaConfigs(quotaConfigs);
        planFeatureRepository.save(pf);
    }

    private Plan createPlan(String code, String name, BigDecimal price, BillingCycle cycle) {
        var p = new Plan();
        p.setCode(code);
        p.setName(name);
        p.setPrice(price);
        p.setBillingCycle(cycle);
        return planRepository.save(p);
    }
}
