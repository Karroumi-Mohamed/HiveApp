package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicyActivation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommercialPolicyActivationRepository
        extends JpaRepository<CommercialPolicyActivation, UUID> {

    @Query("select coalesce(max(activation.activationNumber), 0) "
            + "from CommercialPolicyActivation activation where activation.policy.id = :policyId")
    int findMaximumActivationNumber(@Param("policyId") UUID policyId);

    Optional<CommercialPolicyActivation> findTopByPolicy_IdOrderByActivationNumberDesc(UUID policyId);

    @EntityGraph(attributePaths = "accountIds")
    @Query("select activation from CommercialPolicyActivation activation where activation.id = :activationId")
    Optional<CommercialPolicyActivation> findWithAccountsById(@Param("activationId") UUID activationId);

    Optional<CommercialPolicyActivation> findByIdAndPolicy_Id(UUID activationId, UUID policyId);

    @Query("select accountId from CommercialPolicyActivation activation join activation.accountIds accountId "
            + "where activation.id = :activationId order by accountId")
    List<UUID> findSnapshotAccountIds(@Param("activationId") UUID activationId);

    @Query(value = "select accountId from CommercialPolicyActivation activation "
            + "join activation.accountIds accountId where activation.id = :activationId "
            + "and activation.policy.id = :policyId order by accountId",
            countQuery = "select count(accountId) from CommercialPolicyActivation activation "
                    + "join activation.accountIds accountId where activation.id = :activationId "
                    + "and activation.policy.id = :policyId")
    Page<UUID> findSnapshotAccountIds(
            @Param("policyId") UUID policyId,
            @Param("activationId") UUID activationId,
            Pageable pageable);

    Page<CommercialPolicyActivation> findAllByPolicy_Id(UUID policyId, Pageable pageable);

    @Query("select activation.policy.id, max(activation.activationNumber) "
            + "from CommercialPolicyActivation activation where activation.policy.id in :policyIds "
            + "group by activation.policy.id")
    List<Object[]> findLatestActivationNumbers(@Param("policyIds") Collection<UUID> policyIds);

    @Query("select activation.policy.id, activation.affectedAccountCount "
            + "from CommercialPolicyActivation activation where activation.policy.id in :policyIds "
            + "and activation.activationNumber = (select max(other.activationNumber) "
            + "from CommercialPolicyActivation other where other.policy.id = activation.policy.id)")
    List<Object[]> countLatestSnapshotAccounts(@Param("policyIds") Collection<UUID> policyIds);
}
