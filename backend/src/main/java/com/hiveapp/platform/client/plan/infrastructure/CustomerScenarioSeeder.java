package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.identity.domain.constant.IdentityKind;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.identity.service.NewUserCommand;
import com.hiveapp.platform.admin.config.AdminBootstrapProperties;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountBillingProfileModels;
import com.hiveapp.platform.client.account.service.AccountBillingProfileService;
import com.hiveapp.platform.client.account.service.WorkspaceProvisioningService;
import com.hiveapp.platform.client.company.service.CompanyService;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.service.MemberService;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.service.*;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.shared.security.HiveAppUserDetails;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opt-in local customer bootstrap. Commercial state is created through the same reviewed
 * services as the APIs: no fabricated invoice, payment, entitlement, or execution rows.
 * Each customer and commercial setup stage is atomic, and existing rows are preserved.
 */
@Component
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "hiveapp.customer-seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CustomerScenarioSeeder {
    private static final String COHORT = "LOCAL_SEED: Connected customer workflows v1";
    private static final List<Customer> CUSTOMERS = List.of(
            new Customer("atlas-logistics", "Atlas Logistics", "Amine", "Bennani", "FLEX", "Casablanca"),
            new Customer("marina-studio", "Marina Studio", "Salma", "Idrissi", "PRO", "Rabat"),
            new Customer("cedar-commerce", "Cedar Commerce", "Youssef", "El Amrani", "BUSINESS", "Tangier"),
            new Customer("rif-services", "Rif Services", "Nadia", "Alami", "PRO", "Tetouan"),
            new Customer("sahara-manufacturing", "Sahara Manufacturing", "Omar", "Tazi", "BUSINESS", "Agadir"),
            new Customer("medina-retail", "Medina Retail", "Hajar", "Mansouri", "PRO", "Marrakesh"),
            new Customer("atlas-foundation", "Atlas Foundation", "Leila", "Fassi", "FREE", "Fez"),
            new Customer("mosaic-consulting", "Mosaic Consulting", "Karim", "Berrada", "PRO", "Rabat"),
            new Customer("northstar-partners", "Northstar Partners", "Sara", "Lahlou", "FLEX", "Casablanca"),
            new Customer("nouri-ventures", "Nouri Ventures", "Adam", "Nouri", "FREE", "Rabat"),
            new Customer("bay-operations", "Bay Operations", "Ines", "Saidi", "FREE", "Tangier"));

    private final IdentityService identities;
    private final WorkspaceProvisioningService provisioning;
    private final AccountRepository accounts;
    private final AdminUserRepository admins;
    private final AdminBootstrapProperties bootstrap;
    private final AccountBillingProfileService billingProfiles;
    private final SubscriptionService subscriptions;
    private final SubscriptionCheckoutService checkouts;
    private final SubscriptionLifecycleAdminService lifecycle;
    private final SpecialAgreementService agreements;
    private final BillingAdminService billing;
    private final BillingInvoiceRepository invoices;
    private final CompanyService companies;
    private final MemberService members;
    private final CommercialCampaignAdminService campaigns;
    private final CommercialCampaignRepository campaignRows;
    private final CommercialCampaignLifecycleTransitionService campaignLifecycle;
    private final CommercialOfferAdminService offers;
    private final CommercialOfferRepository offerRows;
    private final CommercialOfferRedemptionRepository redemptionRows;
    private final PlanRepository plans;
    private final ProductPriceRepository prices;
    private final SubscriptionChangeJobService jobs;
    private final SubscriptionChangeJobRepository jobRows;
    private final PlatformTransactionManager transactions;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    private final Clock clock;
    private final EntityManager entityManager;

    @Value("${hiveapp.customer-seed.password:}")
    private String customerPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void seed() throws Exception {
        // Keep this opt-in helper confined to a development H2 datasource.
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            if (!url.startsWith("jdbc:h2:") || url.startsWith("jdbc:h2:tcp:") || url.startsWith("jdbc:h2:ssl:")) {
                throw new IllegalStateException("Customer scenarios require the local development H2 database.");
            }
        }
        if (customerPassword.isBlank() || customerPassword.length() < 16) {
            throw new IllegalStateException("An explicit local customer seed password of at least 16 characters is required.");
        }
        var tx = new TransactionTemplate(transactions);
        Actor administrator = tx.execute(status -> {
            var admin = admins.findByUser_Email(bootstrap.email())
                    .filter(row -> row.isActive() && row.isSuperAdmin() && row.getUser().isActive())
                    .orElseThrow(() -> new IllegalStateException("Customer bootstrap requires the configured active local SuperAdmin."));
            return new Actor(admin.getUser().getId(), admin.getUser().getEmail(), null);
        });
        withActor(administrator, () -> {
            Map<String, UUID> ids = new LinkedHashMap<>();
            for (Customer customer : CUSTOMERS) {
                UUID id = tx.execute(status -> seedCustomer(customer, administrator.userId()));
                ids.put(customer.key(), id);
            }
            seedCommercialCohort(ids, administrator.userId(), tx);
            log.info("Customer scenario bootstrap complete: {} persistent customers; existing scenarios preserved.", ids.size());
            return null;
        });
    }

    private UUID seedCustomer(Customer customer, UUID operatorId) {
        String slug = "seed-" + customer.key();
        Account existing = accounts.findOne((root, query, cb) -> cb.equal(root.get("slug"), slug)).orElse(null);
        if (existing != null) {
            log.info("Customer scenario preserved: {} ({})", existing.getName(), existing.getId());
            return existing.getId();
        }
        String email = customer.key() + "@customers.hive.example";
        if (identities.emailExists(email)) {
            throw new IllegalStateException("Seed identity already exists without its expected workspace: " + email);
        }
        var user = identities.createUser(new NewUserCommand(
                "seed-owner-" + customer.key(), email, customer.firstName(), customer.lastName(), null,
                passwordEncoder.encode(customerPassword), true, true, IdentityKind.CLIENT));
        UUID accountId = provisioning.provision(user.getId(), email).accountId();
        Account account = accounts.findById(accountId).orElseThrow();
        account.setName(customer.name());
        account.setSlug(slug);
        accounts.saveAndFlush(account);
        billingProfiles.update(accountId, new AccountBillingProfileModels.UpdateRequest(
                customer.name(), email, null, customer.city() + ", Morocco", "MA"));

        Set<String> addOns = customer.key().equals("atlas-logistics")
                ? Set.of("ORGANIZATION_TOOLS", "B2B_COLLABORATION") : Set.of();
        List<QuotaPackageSelection> capacity = switch (customer.key()) {
            case "atlas-logistics" -> List.of(new QuotaPackageSelection("MEMBERS_5", 2), new QuotaPackageSelection("COMPANY_1", 1));
            case "marina-studio" -> List.of(new QuotaPackageSelection("MEMBERS_25", 1));
            default -> List.of();
        };
        UUID initialInvoice = null;
        if (!customer.plan().equals("FREE")) {
            var operation = change(accountId, operatorId,
                    new SubscriptionChangeRequest(customer.plan(), addOns, capacity), "Initial customer subscription", true);
            initialInvoice = invoiceFor(operation);
        }

        // Populate real usage through the client owner's permission and quota checks.
        withActor(new Actor(user.getId(), email, accountId), () -> {
            int companyCount = customer.key().equals("atlas-logistics") ? 2 : 1;
            for (int i = 1; i <= companyCount; i++) {
                companies.createCompany(accountId, customer.name() + (i == 1 ? "" : " Distribution"),
                        customer.name(), null, "Services", "MA", customer.city(), null);
            }
            // No email addresses on staff records: creation cannot send invitations externally.
            int staffCount = customer.plan().equals("FREE") ? 1 : 3;
            for (int i = 1; i <= staffCount; i++) {
                members.createMember(accountId, new CreateMemberRequest(
                        "seed-" + customer.key() + "-staff-" + i, null, "Team", "Member " + i,
                        "Team Member " + i, null, "EMP-" + i, List.of()));
            }
            return null;
        });

        switch (customer.key()) {
            case "marina-studio" -> change(accountId, operatorId,
                    new SubscriptionChangeRequest("BUSINESS", Set.of(), List.of(), SubscriptionChangeTiming.AT_RENEWAL),
                    "Move to Business at renewal", true);
            case "cedar-commerce" -> change(accountId, operatorId,
                    new SubscriptionChangeRequest("SCALE", Set.of(), List.of(new QuotaPackageSelection("MEMBERS_100", 1))),
                    "Scale upgrade awaiting settlement", false);
            case "rif-services" -> applyLifecycle(accountId, operatorId, SubscriptionLifecycleAction.SUSPEND);
            case "sahara-manufacturing" -> applyLifecycle(accountId, operatorId, SubscriptionLifecycleAction.CANCEL_AT_PERIOD_END);
            case "medina-retail" -> {
                billing.issueCredit(initialInvoice, new BigDecimal("5.00"), "MAD",
                        reason("Service adjustment"), "LOCAL_SEED", operatorId, "LOCAL-SEED-CREDIT-" + accountId);
                var payment = billing.payments(initialInvoice).stream().filter(p -> p.trustedForSettlement()).findFirst().orElseThrow();
                billing.recordManualRefund(payment.id(), new BigDecimal("3.00"), "MAD", reason("Partial manual refund"),
                        "LOCAL-SEED-REFUND-" + accountId, operatorId, "local-seed-refund-" + accountId);
            }
            case "atlas-foundation" -> agreement(accountId, operatorId, true);
            case "mosaic-consulting" -> agreement(accountId, operatorId, false);
            default -> { }
        }
        log.info("Customer scenario created: {} ({})", customer.name(), accountId);
        return accountId;
    }

    private SubscriptionChangeOperationDto change(UUID accountId, UUID actorId,
            SubscriptionChangeRequest selection, String description, boolean settle) {
        refreshPersistedState();
        var preview = subscriptions.previewChangeAsOperator(accountId, actorId, selection);
        if (!preview.conflicts().isEmpty()) {
            throw new IllegalStateException("Seed selection has conflicts: " + preview.conflicts());
        }
        var result = subscriptions.applyChangeAsOperator(accountId, actorId,
                new SubscriptionChangeApplyRequest(selection, preview.previewToken()), reason(description));
        if (settle && result.operation().checkout() != null) {
            UUID checkoutId = result.operation().checkout().id();
            checkouts.confirmManual(checkoutId, actorId, "LOCAL-SEED-SETTLEMENT-" + checkoutId,
                    reason("Local seed manual settlement; no bank transaction"));
        }
        return result.operation();
    }

    private UUID invoiceFor(SubscriptionChangeOperationDto operation) {
        return invoices.findByCheckoutId(operation.checkout().id()).orElseThrow().getId();
    }

    private void applyLifecycle(UUID accountId, UUID actorId, SubscriptionLifecycleAction action) {
        refreshPersistedState();
        var preview = lifecycle.preview(accountId, actorId, new SubscriptionLifecycleModels.PreviewRequest(action, null));
        if (!preview.blockers().isEmpty()) throw new IllegalStateException("Seed lifecycle is blocked: " + preview.blockers());
        var request = new SubscriptionLifecycleModels.ApplyRequest(preview.previewToken(), reason(action.name()), null);
        if (action == SubscriptionLifecycleAction.SUSPEND) lifecycle.suspend(accountId, actorId, request);
        else lifecycle.cancelAtPeriodEnd(accountId, actorId, request);
    }

    private void agreement(UUID accountId, UUID actorId, boolean complimentary) {
        refreshPersistedState();
        Instant start = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(3).toInstant();
        var definition = new SpecialAgreementModels.Definition(
                new SubscriptionChangeRequest("ENTERPRISE", Set.of(), List.of()), List.of(), start, end,
                complimentary ? SpecialAgreementPricingMode.COMPLIMENTARY : SpecialAgreementPricingMode.CUSTOM_TOTAL,
                complimentary ? null : new BigDecimal("240.00"), "MAD",
                complimentary ? SpecialAgreementSettlementMode.NONE : SpecialAgreementSettlementMode.MANUAL,
                SpecialAgreementEndInstruction.RESTORE_PREVIOUS_TERMS, null, null);
        var preview = agreements.preview(accountId, actorId, definition);
        if (!preview.confirmable()) throw new IllegalStateException("Seed agreement is blocked: " + preview.conflicts());
        agreements.confirm(accountId, actorId, new SpecialAgreementModels.ConfirmRequest(
                definition, preview.previewToken(), reason(complimentary ? "Three-month complimentary agreement" : "Negotiated Enterprise agreement awaiting settlement")));
    }

    private void seedCommercialCohort(Map<String, UUID> ids, UUID actorId, TransactionTemplate tx) {
        // Commit each setup stage before eligibility/acceptance: Offer redemption deliberately
        // uses independent transactions. Persisted artifacts make interrupted bootstrap resumable.
        Instant start = clock.instant().minusSeconds(30);
        Instant end = start.plus(90, ChronoUnit.DAYS);
        UUID campaignId = tx.execute(status -> campaignRows.findOne(
                (root, query, cb) -> cb.like(root.get("code"), "LOCAL\\_SEED\\_V1\\_PARTNER\\_ONBOARDING\\_%", '\\'))
                .map(row -> row.getId()).orElseGet(() -> {
            var audience = new CommercialCampaignRequests.Audience(CommercialCampaignAudienceMode.EXPLICIT_ACCOUNTS,
                    Set.of(ids.get("northstar-partners"), ids.get("cedar-commerce")), null, null);
            // The generated immutable business code identifies this seed even after an operator
            // edits its display name/reason. No persistence setter bypasses catalog validation.
            var created = campaigns.create(new CommercialCampaignRequests.Create(
                "Local Seed v1 Partner Onboarding", "Pro onboarding for the selected partner accounts.", start, end,
                CommercialCampaignSource.SALES, COHORT,
                audience));
            campaigns.update(created.campaignId(), new CommercialCampaignRequests.Update(created.version(),
                    "Partner onboarding", "Pro onboarding for the selected partner accounts.", start, end,
                    CommercialCampaignSource.SALES, COHORT, audience));
            return created.campaignId();
        }));
        tx.executeWithoutResult(status -> {
            if (campaigns.operations(campaignId).status() != CommercialCampaignStatus.DRAFT) return;
            var schedule = campaigns.previewSchedule(campaignId);
            if (!schedule.schedulable()) throw new IllegalStateException("Seed campaign is blocked: " + schedule.blockers());
            campaigns.schedule(campaignId, new CommercialCampaignRequests.Schedule(
                    schedule.campaignVersion(), COHORT, schedule.previewToken()));
        });
        campaignLifecycle.processDueStart(campaignId, clock.instant());

        UUID offerId = tx.execute(status -> offerRows.findAll(
                (root, query, cb) -> cb.equal(root.get("lineage").get("campaign").get("id"), campaignId),
                PageRequest.of(0, 1, Sort.by("createdAt").ascending().and(Sort.by("id"))))
                .stream().findFirst().map(row -> row.getId()).orElseGet(() -> {
            var pro = plans.findByCode("PRO").orElseThrow();
            var price = prices.findApplicable(ProductPriceOwnerType.PLAN, pro.getId(), "MAD", BillingCycle.MONTHLY, clock.instant())
                    .stream().findFirst().orElseThrow();
            var campaign = campaigns.get(campaignId).summary();
            return offers.create(new CommercialOfferRequests.Create(
                "Partner Pro onboarding", "Complimentary Pro terms with ten extra members.", campaignId, campaign.startsAt(), campaign.endsAt(),
                CommercialOfferDiscovery.CATALOG, CommercialOfferAcceptance.OPERATOR_ONLY, null, 10L, 1L,
                new CommercialOfferSelection(pro.getId(), price.getId(), List.of(), List.of(), SubscriptionChangeTiming.IMMEDIATE),
                new CommercialOfferEffectSnapshot(CommercialOfferDiscountType.FIXED, price.money().amount(), null, null,
                        List.of(new CommercialOfferEffectSnapshot.QuotaBonus(StaffFeature.CODE, StaffFeature.MEMBERS, 10))))).offerId();
        }));
        tx.executeWithoutResult(status -> {
            if (offers.operations(offerId).status() != CommercialOfferStatus.DRAFT) return;
            var publication = offers.previewPublication(offerId);
            if (!publication.ready()) throw new IllegalStateException("Seed offer is blocked: " + publication.blockers());
            offers.publish(offerId, new CommercialOfferRequests.Publish(publication.expectedVersion(), COHORT, publication.previewToken()));
        });
        boolean accepted = Boolean.TRUE.equals(tx.execute(status -> redemptionRows.count((root, query, cb) -> cb.and(
                cb.equal(root.get("offer").get("id"), offerId),
                cb.equal(root.get("account").get("id"), ids.get("northstar-partners")))) > 0));
        if (!accepted) {
            var eligibility = offers.previewForAccount(offerId, ids.get("northstar-partners"));
            if (!eligibility.eligible()) throw new IllegalStateException("Seed offer acceptance is blocked: " + eligibility.blockers());
            offers.applyForAccount(offerId, ids.get("northstar-partners"), "local-seed-partner-onboarding-v1",
                    new CommercialOfferRequests.OperatorAccept(eligibility.preview().previewToken(), COHORT));
        }

        // The actual worker executes this reviewed mixed audience after the transaction commits.
        tx.executeWithoutResult(status -> queueJob(List.of(ids.get("nouri-ventures"), ids.get("bay-operations"), ids.get("rif-services")), actorId,
                new SubscriptionChangeRequest("PRO", Set.of(), List.of()), "Partner upgrade: ready and suspended accounts", null));
        tx.executeWithoutResult(status -> queueJob(List.of(ids.get("atlas-logistics")), actorId,
                new SubscriptionChangeRequest("SCALE", Set.of(), List.of(), SubscriptionChangeTiming.AT_RENEWAL),
                "Scheduled Scale migration", clock.instant().plus(2, ChronoUnit.DAYS)));
        log.info("Customer commercial cohort ready: campaign, Offer redemption, mixed-result job and scheduled job.");
    }

    private void queueJob(List<UUID> accountIds, UUID actorId, SubscriptionChangeRequest selection, String description, Instant executeAt) {
        if (jobRows.findAll().stream().anyMatch(row -> row.getReason().equals(reason(description)))) return;
        var preview = jobs.preview(actorId, new SubscriptionChangeJobModels.PreviewRequest(accountIds, selection, reason(description), executeAt));
        jobs.confirm(preview.jobId(), actorId, new SubscriptionChangeJobModels.ConfirmRequest(preview.previewToken()));
    }

    private String reason(String description) { return "LOCAL_SEED: " + description; }

    private void refreshPersistedState() {
        // Signed reviews must see the database's canonical decimal scale and row versions,
        // just as separate API requests do. The commercial finalizer also clears its context.
        entityManager.flush();
        entityManager.clear();
    }

    private <T> T withActor(Actor actor, Supplier<T> action) {
        var previousSecurity = SecurityContextHolder.getContext();
        var previousPermissions = HiveAppContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HiveAppUserDetails(actor.userId(), actor.email(), "", true), "", List.of()));
        SecurityContextHolder.setContext(context);
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actor.userId(), actor.accountId(), actor.accountId(), null, null, false));
        try { return action.get(); }
        finally {
            SecurityContextHolder.setContext(previousSecurity);
            if (previousPermissions == null) HiveAppContextHolder.clearContext();
            else HiveAppContextHolder.setContext(previousPermissions);
        }
    }

    private record Actor(UUID userId, String email, UUID accountId) {}
    private record Customer(String key, String name, String firstName, String lastName, String plan, String city) {}
}
