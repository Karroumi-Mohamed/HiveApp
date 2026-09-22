package com.hiveapp.platform.communication;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "communication_preferences")
@Getter
@Setter
public class CommunicationPreference {
  @Id private UUID accountId;
  private boolean marketingInApp;
  private boolean marketingEmail;
}
