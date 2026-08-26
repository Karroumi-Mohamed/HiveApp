package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AddOnFeatureRepository extends JpaRepository<AddOnFeature, UUID> {
    List<AddOnFeature> findAllByAddOnId(UUID addOnId);
    Optional<AddOnFeature> findByAddOnIdAndFeature_Code(UUID addOnId, String featureCode);

    @EntityGraph(attributePaths = {"addOn", "feature"})
    @Query("select item from AddOnFeature item where item.addOn.id in :addOnIds order by item.id")
    List<AddOnFeature> findAllByAddOnIds(@Param("addOnIds") Collection<UUID> addOnIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"addOn", "feature"})
    @Query("select item from AddOnFeature item where item.addOn.id in :addOnIds order by item.id")
    List<AddOnFeature> findAllByAddOnIdsForUpdate(@Param("addOnIds") Collection<UUID> addOnIds);

    @Query("""
            select item.addOn.id, count(item)
            from AddOnFeature item
            where item.addOn.id in :addOnIds
            group by item.addOn.id
            """)
    List<Object[]> countByAddOnIds(
            @org.springframework.data.repository.query.Param("addOnIds") Collection<UUID> addOnIds);
}
