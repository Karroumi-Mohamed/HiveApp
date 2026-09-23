package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "communication_publications")
@Getter
@Setter
public class CommunicationPublication extends BaseEntity {
  @Version private long version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Kind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Purpose purpose;

  @Column(nullable = false, length = 160)
  private String messageTitle;

  @Column(nullable = false, length = 10000)
  private String messageBody;

  @Embedded private ManualNotificationLanguages languages;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private List<UUID> accountIds;

  private boolean email;
  private boolean replies;
  private UUID offerId;

  @Column(nullable = false)
  private Instant availableAt;

  private Instant expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private State state = State.DRAFT;

  @Column(nullable = false)
  private UUID actorId;

  @Column(length = 2000)
  private String reason;
}
