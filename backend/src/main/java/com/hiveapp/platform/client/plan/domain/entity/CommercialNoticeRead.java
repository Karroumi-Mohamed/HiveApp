package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "commercial_notice_reads",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_commercial_notice_reader",
            columnNames = {"notice_id", "user_id"}))
@Getter
@Setter
public class CommercialNoticeRead extends BaseEntity {
  @Column(name = "notice_id", nullable = false)
  private UUID noticeId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private Instant readAt;
}
