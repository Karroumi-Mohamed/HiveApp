package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialNoticeRead;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialNoticeReadRepository extends JpaRepository<CommercialNoticeRead, UUID> {
  Optional<CommercialNoticeRead> findByNoticeIdAndUserId(UUID noticeId, UUID userId);
  @org.springframework.data.jpa.repository.Query("select r.noticeId, count(r) from CommercialNoticeRead r where r.noticeId in :ids group by r.noticeId")
  List<Object[]> counts(@org.springframework.data.repository.query.Param("ids") Collection<UUID> ids);
  long countByNoticeId(UUID id);
  boolean existsByNoticeIdAndUserId(UUID noticeId, UUID userId);

  List<CommercialNoticeRead> findAllByUserIdAndNoticeIdIn(UUID userId, Collection<UUID> ids);
}
