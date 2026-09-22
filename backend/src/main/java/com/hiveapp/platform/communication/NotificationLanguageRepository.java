package com.hiveapp.platform.communication;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLanguageRepository
    extends JpaRepository<NotificationLanguagePreference, UUID> {}
