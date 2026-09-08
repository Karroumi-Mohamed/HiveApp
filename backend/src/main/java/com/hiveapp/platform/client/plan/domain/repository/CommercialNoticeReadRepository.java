package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialNoticeRead;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialNoticeReadRepository extends JpaRepository<CommercialNoticeRead, UUID> {
  boolean existsByNoticeIdAndUserId(UUID noticeId, UUID userId);

  List<CommercialNoticeRead> findAllByUserIdAndNoticeIdIn(UUID userId, Collection<UUID> ids);
}
