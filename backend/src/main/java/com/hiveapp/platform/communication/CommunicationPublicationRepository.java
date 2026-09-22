package com.hiveapp.platform.communication;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CommunicationPublicationRepository
    extends JpaRepository<CommunicationPublication, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from CommunicationPublication p where p.id=:id")
  Optional<CommunicationPublication> lock(@Param("id") UUID id);
}
