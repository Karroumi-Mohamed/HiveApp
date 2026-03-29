package com.hiveapp.platform.client.collaboration.domain.repository;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface CollaborationRepository extends JpaRepository<Collaboration, UUID> {
    List<Collaboration> findAllByClientAccountId(UUID accountId);
    List<Collaboration> findAllByProviderAccountId(UUID accountId);
}
