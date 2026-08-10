package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddOnRepository extends JpaRepository<AddOn, UUID> {
    Optional<AddOn> findByCode(String code);
    List<AddOn> findAllByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = {"features", "features.feature"})
    List<AddOn> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = {"features", "features.feature"})
    Optional<AddOn> findDetailedById(UUID id);
}
