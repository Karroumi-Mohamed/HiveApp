package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.platform.admin.dto.PlatformObservabilityModels;
import com.hiveapp.platform.admin.dto.PlatformObservabilityModels.ComponentState;
import com.hiveapp.platform.admin.service.PlatformObservabilityService;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import com.hiveapp.platform.client.plan.domain.repository.BillingProviderEventRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.ObservabilityFeature;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import com.hiveapp.shared.email.delivery.EmailDeliveryRepository;
import com.hiveapp.shared.email.delivery.EmailDeliveryStatus;
import com.hiveapp.shared.observability.ObservabilityProperties;
import com.hiveapp.shared.payment.BillingProperties;
import com.hiveapp.shared.payment.PaymentGateway;
import dev.karroumi.permissionizer.PermissionNode;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PermissionNode(key = ObservabilityFeature.KEY, description = "Platform Observability",
        guard = PermissionNode.Guard.ON)
public class PlatformObservabilityServiceImpl extends PlatformControlFeatureService
        implements PlatformObservabilityService {
    private final DataSource dataSource;
    private final Environment environment;
    private final BillingProperties billing;
    private final List<PaymentGateway> paymentGateways;
    private final BillingOutboxCommandRepository outbox;
    private final BillingProviderEventRepository providerEvents;
    private final EmailDeliveryRepository emailDeliveries;
    private final ObservabilityProperties observability;
    private final Clock clock;

    public PlatformObservabilityServiceImpl(
            DataSource dataSource,
            Environment environment,
            BillingProperties billing,
            List<PaymentGateway> paymentGateways,
            BillingOutboxCommandRepository outbox,
            BillingProviderEventRepository providerEvents,
            EmailDeliveryRepository emailDeliveries,
            ObservabilityProperties observability,
            Clock clock
    ) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.billing = billing;
        this.paymentGateways = paymentGateways;
        this.outbox = outbox;
        this.providerEvents = providerEvents;
        this.emailDeliveries = emailDeliveries;
        this.observability = observability;
        this.clock = clock;
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return ObservabilityFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_health", description = "Read safe application readiness")
    public PlatformObservabilityModels.Health health() {
        List<PlatformObservabilityModels.Component> components = new ArrayList<>();
        components.add(databaseHealth());
        components.add(emailHealth());
        components.add(billingHealth());
        return new PlatformObservabilityModels.Health(clock.instant(), List.copyOf(components));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_backlogs", description = "Read safe internal queue backlog counts")
    public PlatformObservabilityModels.Backlogs backlogs() {
        return new PlatformObservabilityModels.Backlogs(clock.instant(), List.of(
                outboxBacklog(), providerEventBacklog(), emailBacklog()));
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_log_access",
            description = "Read external log-provider availability and destination")
    public PlatformObservabilityModels.LogAccess logAccess() {
        String provider = normalized(observability.getLogs().getProvider());
        String destination = safeHttpUrl(observability.getLogs().getUrl());
        boolean configured = provider != null && destination != null;
        return new PlatformObservabilityModels.LogAccess(
                configured,
                configured ? provider : null,
                configured ? destination : null,
                configured
                        ? "Use a request id from HiveApp to continue the investigation in the external provider."
                        : "No external log provider is configured. HiveApp does not store raw production logs.");
    }

    private PlatformObservabilityModels.Component databaseHealth() {
        try (var connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return new PlatformObservabilityModels.Component(
                    "database", "Database", valid ? ComponentState.UP : ComponentState.UNAVAILABLE,
                    valid ? null : "Database readiness failed; inspect infrastructure health.");
        } catch (Exception ignored) {
            return new PlatformObservabilityModels.Component(
                    "database", "Database", ComponentState.UNAVAILABLE,
                    "Database readiness failed; inspect infrastructure health.");
        }
    }

    private PlatformObservabilityModels.Component emailHealth() {
        String mailHost = normalized(environment.getProperty("spring.mail.host"));
        boolean nonProduction = List.of(environment.getActiveProfiles()).stream()
                .anyMatch(profile -> profile.equals("dev") || profile.equals("test"));
        if (mailHost != null) {
            return new PlatformObservabilityModels.Component(
                    "email", "Credential email", ComponentState.CONFIGURED, null);
        }
        if (nonProduction) {
            return new PlatformObservabilityModels.Component(
                    "email", "Credential email", ComponentState.SUPPRESSED,
                    "Development transport records delivery as suppressed and sends no email.");
        }
        return new PlatformObservabilityModels.Component(
                "email", "Credential email", ComponentState.UNAVAILABLE,
                "No credential-email transport is configured.");
    }

    private PlatformObservabilityModels.Component billingHealth() {
        boolean trusted = paymentGateways.stream().anyMatch(PaymentGateway::trustedForSettlement);
        if (!billing.isCollectionEnabled()) {
            return new PlatformObservabilityModels.Component(
                    "billing_provider", "Payment collection", ComponentState.DISABLED,
                    "Automatic provider collection is disabled.");
        }
        return new PlatformObservabilityModels.Component(
                "billing_provider", "Payment collection",
                trusted ? ComponentState.CONFIGURED : ComponentState.UNAVAILABLE,
                trusted ? null : "Collection is enabled without a trusted provider.");
    }

    private PlatformObservabilityModels.Backlog outboxBacklog() {
        EnumMap<BillingOutboxStatus, Long> counts = new EnumMap<>(BillingOutboxStatus.class);
        for (BillingOutboxStatus status : BillingOutboxStatus.values()) counts.put(status, 0L);
        Instant oldest = null;
        for (var row : outbox.countByStatus()) {
            counts.put(row.getValue(), row.getTotal());
            if (row.getValue() == BillingOutboxStatus.PENDING
                    || row.getValue() == BillingOutboxStatus.PROCESSING
                    || row.getValue() == BillingOutboxStatus.FAILED) {
                oldest = earlier(oldest, row.getOldest());
            }
        }
        boolean attention = counts.get(BillingOutboxStatus.FAILED) > 0
                || counts.get(BillingOutboxStatus.PROCESSING) > 0;
        return backlog("billing_outbox", "Payment commands", counts, oldest, attention,
                "/admin/billing?view=provider-commands");
    }

    private PlatformObservabilityModels.Backlog providerEventBacklog() {
        EnumMap<BillingProviderEventStatus, Long> counts = new EnumMap<>(BillingProviderEventStatus.class);
        for (BillingProviderEventStatus status : BillingProviderEventStatus.values()) counts.put(status, 0L);
        Instant oldest = null;
        for (var row : providerEvents.countByStatus()) {
            counts.put(row.getValue(), row.getTotal());
            if (row.getValue() != BillingProviderEventStatus.APPLIED) {
                oldest = earlier(oldest, row.getOldest());
            }
        }
        boolean attention = counts.get(BillingProviderEventStatus.UNMATCHED) > 0
                || counts.get(BillingProviderEventStatus.MISMATCHED) > 0
                || counts.get(BillingProviderEventStatus.RECEIVED) > 0;
        return backlog("provider_events", "Provider events", counts, oldest, attention,
                "/admin/billing?view=provider-events");
    }

    private PlatformObservabilityModels.Backlog emailBacklog() {
        EnumMap<EmailDeliveryStatus, Long> counts = new EnumMap<>(EmailDeliveryStatus.class);
        for (EmailDeliveryStatus status : EmailDeliveryStatus.values()) counts.put(status, 0L);
        Instant oldest = null;
        for (var row : emailDeliveries.countByStatus()) {
            counts.put(row.getValue(), row.getTotal());
            if (row.getValue() == EmailDeliveryStatus.PENDING
                    || row.getValue() == EmailDeliveryStatus.FAILED) {
                oldest = earlier(oldest, row.getOldest());
            }
        }
        boolean attention = counts.get(EmailDeliveryStatus.PENDING) > 0
                || counts.get(EmailDeliveryStatus.FAILED) > 0;
        return backlog("email_delivery", "Credential email", counts, oldest, attention,
                "/admin/communications");
    }

    private PlatformObservabilityModels.Backlog backlog(
            String key,
            String label,
            Map<? extends Enum<?>, Long> values,
            Instant oldest,
            boolean attention,
            String destination
    ) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        values.forEach((status, count) -> counts.put(status.name(), count));
        return new PlatformObservabilityModels.Backlog(
                key, label, Map.copyOf(counts), oldest, attention, destination);
    }

    private Instant earlier(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String safeHttpUrl(String value) {
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (normalized == null || normalized.length() > 2_048) return null;
        try {
            URI uri = URI.create(normalized);
            return ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null
                    ? uri.toString() : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
