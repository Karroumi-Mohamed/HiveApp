package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommercialPolicyRepository extends JpaRepository<CommercialPolicy, UUID>,
        JpaSpecificationExecutor<CommercialPolicy> {

    boolean existsByCode(String code);

    @Query("select policy.lineageId from CommercialPolicy policy where policy.id = :policyId")
    Optional<UUID> findLineageIdById(@Param("policyId") UUID policyId);

    @Query("select policy.owner.id from CommercialPolicy policy where policy.id = :policyId")
    Optional<UUID> findOwnerAdminUserIdById(@Param("policyId") UUID policyId);

    @Override
    @EntityGraph(attributePaths = {"targetAccount", "targetPlan"})
    Page<CommercialPolicy> findAll(Specification<CommercialPolicy> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"targetAccount", "targetPlan", "effects",
            "effects.plan", "effects.addOn", "effects.quotaPackage", "effects.feature"})
    @Query("select distinct policy from CommercialPolicy policy where policy.id = :policyId")
    Optional<CommercialPolicy> findDetailById(@Param("policyId") UUID policyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from CommercialPolicy policy where policy.id = :policyId")
    Optional<CommercialPolicy> findByIdForUpdate(@Param("policyId") UUID policyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from CommercialPolicy policy where policy.lineageId = :lineageId "
            + "order by policy.revisionNumber asc, policy.id asc")
    List<CommercialPolicy> findLineageForUpdate(@Param("lineageId") UUID lineageId);

    Page<CommercialPolicy> findAllByLineageId(UUID lineageId, Pageable pageable);

    @Query("select coalesce(max(policy.revisionNumber), 0) from CommercialPolicy policy "
            + "where policy.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

    @Query("select policy.lineageId, max(policy.revisionNumber) from CommercialPolicy policy "
            + "where policy.lineageId in :lineageIds group by policy.lineageId")
    List<Object[]> findMaximumRevisionNumbers(@Param("lineageIds") Collection<UUID> lineageIds);

    @Query("select policy.lineageId, max(policy.revisionNumber) from CommercialPolicy policy "
            + "where policy.lineageId in :lineageIds and (policy.status <> :archived "
            + "or exists (select activation.id from CommercialPolicyActivation activation "
            + "where activation.policy = policy)) group by policy.lineageId")
    List<Object[]> findMaximumMaterialRevisionNumbers(
            @Param("lineageIds") Collection<UUID> lineageIds,
            @Param("archived") CommercialPolicyStatus archived);

    @Query("select coalesce(max(policy.revisionNumber), 0) from CommercialPolicy policy "
            + "where policy.lineageId = :lineageId and (policy.status <> :archived "
            + "or exists (select activation.id from CommercialPolicyActivation activation "
            + "where activation.policy = policy))")
    int findMaximumMaterialRevisionNumber(
            @Param("lineageId") UUID lineageId,
            @Param("archived") CommercialPolicyStatus archived);

    @Query("select policy.id, count(effect) from CommercialPolicy policy left join policy.effects effect "
            + "where policy.id in :policyIds group by policy.id")
    List<Object[]> countEffectsByPolicyIds(@Param("policyIds") Collection<UUID> policyIds);

    @Query("select policy.id, count(account) from CommercialPolicy policy "
            + "left join policy.explicitAccounts account where policy.id in :policyIds group by policy.id")
    List<Object[]> countExplicitAccountsByPolicyIds(@Param("policyIds") Collection<UUID> policyIds);

    @Query("select count(account) from CommercialPolicy policy "
            + "join policy.explicitAccounts account where policy.id = :policyId")
    long countExplicitAccounts(@Param("policyId") UUID policyId);

    @Query("select account.id from CommercialPolicy policy "
            + "join policy.explicitAccounts account where policy.id = :policyId order by account.id")
    List<UUID> findExplicitAccountIds(@Param("policyId") UUID policyId);

    @Query("select policy from CommercialPolicy policy where policy.lineageId = :lineageId "
            + "and policy.status in :statuses")
    List<CommercialPolicy> findAllByLineageIdAndStatusIn(
            @Param("lineageId") UUID lineageId,
            @Param("statuses") Collection<CommercialPolicyStatus> statuses);
}
