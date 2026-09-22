package com.hiveapp.platform.communication;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSendCommandRepository extends JpaRepository<NotificationSendCommand, String> {
  Page<NotificationSendCommand> findByAccountIdAndSenderUserId(UUID accountId, UUID senderUserId, Pageable page);
}
