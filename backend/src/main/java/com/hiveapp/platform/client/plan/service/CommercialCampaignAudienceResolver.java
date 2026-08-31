package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.dto.AccountIdentityDirectoryEntryDto;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegment;
import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentActivation;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentActivationRepository;
import com.hiveapp.platform.client.plan.domain.repository.CommercialSegmentRepository;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves Campaign audiences without ever expanding a PUBLIC audience into Account rows. */
@Service
@RequiredArgsConstructor
public class CommercialCampaignAudienceResolver {

    public static final int SNAPSHOT_ACCOUNT_LIMIT = 10_000;
    public static final int SAMPLE_LIMIT = 25;

    private final CommercialCampaignRepository campaignRepository;
    private final CommercialSegmentRepository segmentRepository;
    private final CommercialSegmentActivationRepository activationRepository;
    private final AccountDirectoryService accountDirectoryService;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Evaluation evaluate(CommercialCampaign campaign, Instant evaluatedAt) {
        List<UUID> accountIds = switch (campaign.getAudienceMode()) {
            case PUBLIC -> List.of();
            case EXPLICIT_ACCOUNTS -> campaignRepository.findExplicitAccountIds(campaign.getId());
            case SEGMENT -> segmentAudience(campaign);
        };
        List<UUID> normalized = accountIds.stream().distinct().sorted().toList();
        boolean withinLimit = normalized.size() <= SNAPSHOT_ACCOUNT_LIMIT;
        List<CommercialCampaignBlocker> blockers = new ArrayList<>();
        if (campaign.getAudienceMode() != CommercialCampaignAudienceMode.PUBLIC && normalized.isEmpty()) {
            blockers.add(CommercialCampaignBlocker.EMPTY_AUDIENCE);
        }
        if (!withinLimit) blockers.add(CommercialCampaignBlocker.AUDIENCE_TOO_LARGE);
        if (campaign.getAudienceMode() == CommercialCampaignAudienceMode.SEGMENT) {
            segmentBlockers(campaign, blockers);
        }
        List<UUID> accepted = withinLimit ? normalized : List.of();
        return new Evaluation(campaign.getAudienceMode() == CommercialCampaignAudienceMode.PUBLIC,
                normalized.size(), accepted, normalized.stream().limit(SAMPLE_LIMIT).toList(),
                fingerprint(campaign, accepted), evaluatedAt,
                campaign.getSegment() == null ? null : campaign.getSegment().getId(),
                campaign.getSegmentActivation() == null ? null : campaign.getSegmentActivation().getId(),
                List.copyOf(blockers));
    }

    @Transactional(readOnly = true)
    public List<CommercialCampaignViews.AudienceIdentity> identities(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<UUID, AccountIdentityDirectoryEntryDto> accounts =
                accountDirectoryService.resolveIdentities(ids).stream()
                        .collect(Collectors.toMap(
                                AccountIdentityDirectoryEntryDto::id, Function.identity()));
        return ids.stream().distinct().map(id -> {
            AccountIdentityDirectoryEntryDto account = accounts.get(id);
            if (account == null) {
                return new CommercialCampaignViews.AudienceIdentity(id, null, null, null, false);
            }
            return new CommercialCampaignViews.AudienceIdentity(id, account.name(), account.slug(),
                    account.ownerEmail(), account.active());
        }).toList();
    }

    private List<UUID> segmentAudience(CommercialCampaign campaign) {
        if (campaign.getSegment() == null || campaign.getSegmentActivation() == null) return List.of();
        return activationRepository.findWithAccountsById(campaign.getSegmentActivation().getId())
                .filter(activation -> activation.getSegment().getId().equals(campaign.getSegment().getId()))
                .map(activation -> activation.getAccountIds().stream().sorted().toList())
                .orElse(List.of());
    }

    private void segmentBlockers(CommercialCampaign campaign,
                                 List<CommercialCampaignBlocker> blockers) {
        UUID segmentId = campaign.getSegment() == null ? null : campaign.getSegment().getId();
        UUID activationId = campaign.getSegmentActivation() == null
                ? null : campaign.getSegmentActivation().getId();
        if (segmentId == null || activationId == null) {
            blockers.add(CommercialCampaignBlocker.SEGMENT_ACTIVATION_STALE);
            return;
        }
        CommercialSegment segment = segmentRepository.findDetailById(segmentId).orElse(null);
        if (segment == null || segment.getStatus() != CommercialSegmentStatus.ACTIVE) {
            blockers.add(CommercialCampaignBlocker.SEGMENT_NOT_ACTIVE);
        }
        boolean latest = activationRepository.findTopBySegment_IdOrderByActivationNumberDesc(segmentId)
                .map(CommercialSegmentActivation::getId).filter(activationId::equals).isPresent();
        if (!latest) blockers.add(CommercialCampaignBlocker.SEGMENT_ACTIVATION_STALE);
    }

    private String fingerprint(CommercialCampaign campaign, List<UUID> accountIds) {
        String canonical = String.join("|",
                campaign.getId().toString(),
                campaign.getLineageId().toString(),
                Integer.toString(campaign.getRevisionNumber()),
                Long.toString(campaign.getVersion()),
                campaign.getAudienceMode().name(),
                campaign.getStartsAt().toString(),
                campaign.getEndsAt().toString(),
                campaign.getSegment() == null ? "" : campaign.getSegment().getId().toString(),
                campaign.getSegmentActivation() == null ? "" : campaign.getSegmentActivation().getId().toString(),
                accountIds.stream().sorted(Comparator.naturalOrder()).map(UUID::toString)
                        .collect(Collectors.joining(",")));
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Evaluation(boolean publicAudience, long total, List<UUID> accountIds,
                             List<UUID> sampleIds, String fingerprint, Instant evaluatedAt,
                             UUID segmentId, UUID segmentActivationId,
                             List<CommercialCampaignBlocker> blockers) {
        public Evaluation {
            accountIds = List.copyOf(accountIds);
            sampleIds = List.copyOf(sampleIds);
            blockers = List.copyOf(blockers);
        }
    }
}
