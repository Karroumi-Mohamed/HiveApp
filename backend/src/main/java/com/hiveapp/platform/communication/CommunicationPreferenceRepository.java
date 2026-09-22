package com.hiveapp.platform.communication;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationPreferenceRepository
    extends JpaRepository<CommunicationPreference, UUID> {}
