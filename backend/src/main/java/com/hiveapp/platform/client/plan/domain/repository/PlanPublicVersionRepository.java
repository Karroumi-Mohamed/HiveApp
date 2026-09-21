package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.PlanPublicVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlanPublicVersionRepository extends JpaRepository<PlanPublicVersion, UUID> {
    @EntityGraph(attributePaths = "plan")
    List<PlanPublicVersion> findAllByLineageIdIn(Collection<UUID> lineageIds);
}
