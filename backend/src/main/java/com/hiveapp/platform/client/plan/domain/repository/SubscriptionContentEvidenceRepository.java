package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.SubscriptionContentEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionContentEvidenceRepository
    extends JpaRepository<SubscriptionContentEvidence, UUID> {
  Optional<SubscriptionContentEvidence> findByCommandId(UUID commandId);

  Page<SubscriptionContentEvidence> findAllByAccountIdOrderByEffectiveAtDesc(
      UUID accountId, Pageable pageable);
}
