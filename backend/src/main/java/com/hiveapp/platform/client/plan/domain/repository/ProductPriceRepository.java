package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID>,
        JpaSpecificationExecutor<ProductPrice> {

    @Override
    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    Optional<ProductPrice> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    @Query("select price from ProductPrice price where price.id = :priceId")
    Optional<ProductPrice> findByIdForUpdate(@Param("priceId") UUID priceId);

    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    @Query("""
            select price from ProductPrice price
            where price.id = :priceId
              and price.ownerType = :ownerType
              and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
                    and price.plan.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
                    and price.addOn.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
                    and price.quotaPackage.id = :ownerId))
            """)
    Optional<ProductPrice> findOwned(@Param("ownerType") ProductPriceOwnerType ownerType,
                                     @Param("ownerId") UUID ownerId,
                                     @Param("priceId") UUID priceId);

    @Query("""
            select count(price) from ProductPrice price
            where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
              and price.id <> :excludedId
              and price.ownerType = :ownerType
              and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
                    and price.plan.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
                    and price.addOn.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
                    and price.quotaPackage.id = :ownerId))
              and price.currencyCode = :currencyCode
              and price.billingCycle = :billingCycle
              and (:effectiveUntil is null or price.effectiveFrom < :effectiveUntil)
              and (price.effectiveUntil is null or price.effectiveUntil > :effectiveFrom)
            """)
    long countActiveOverlaps(@Param("ownerType") ProductPriceOwnerType ownerType,
                             @Param("ownerId") UUID ownerId,
                             @Param("currencyCode") String currencyCode,
                             @Param("billingCycle") BillingCycle billingCycle,
                             @Param("effectiveFrom") Instant effectiveFrom,
                             @Param("effectiveUntil") Instant effectiveUntil,
                             @Param("excludedId") UUID excludedId);

    @Query("""
            select count(price) from ProductPrice price
            where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
              and price.id not in :excludedIds
              and price.ownerType = :ownerType
              and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
                    and price.plan.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
                    and price.addOn.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
                    and price.quotaPackage.id = :ownerId))
              and price.currencyCode = :currencyCode
              and price.billingCycle = :billingCycle
              and (:effectiveUntil is null or price.effectiveFrom < :effectiveUntil)
              and (price.effectiveUntil is null or price.effectiveUntil > :effectiveFrom)
            """)
    long countActiveOverlapsExcluding(
            @Param("ownerType") ProductPriceOwnerType ownerType,
            @Param("ownerId") UUID ownerId,
            @Param("currencyCode") String currencyCode,
            @Param("billingCycle") BillingCycle billingCycle,
            @Param("effectiveFrom") Instant effectiveFrom,
            @Param("effectiveUntil") Instant effectiveUntil,
            @Param("excludedIds") Collection<UUID> excludedIds);

    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    @Query("""
            select price from ProductPrice price
            where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
              and price.ownerType = :ownerType
              and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
                    and price.plan.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
                    and price.addOn.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
                    and price.quotaPackage.id = :ownerId))
              and price.currencyCode = :currencyCode
              and price.billingCycle = :billingCycle
              and price.effectiveFrom <= :at
              and (price.effectiveUntil is null or price.effectiveUntil > :at)
            order by price.revisionNumber desc, price.id asc
            """)
    List<ProductPrice> findApplicable(@Param("ownerType") ProductPriceOwnerType ownerType,
                                      @Param("ownerId") UUID ownerId,
                                      @Param("currencyCode") String currencyCode,
                                      @Param("billingCycle") BillingCycle billingCycle,
                                      @Param("at") Instant at);

    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    @Query("""
            select price from ProductPrice price
            where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
              and price.ownerType = :ownerType
              and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
                    and price.plan.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
                    and price.addOn.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
                    and price.quotaPackage.id = :ownerId))
              and price.effectiveFrom <= :at
              and (price.effectiveUntil is null or price.effectiveUntil > :at)
            order by price.currencyCode, price.billingCycle, price.revisionNumber desc, price.id asc
            """)
    List<ProductPrice> findAllApplicable(@Param("ownerType") ProductPriceOwnerType ownerType,
                                         @Param("ownerId") UUID ownerId,
                                         @Param("at") Instant at);

    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    @Query("""
            select price from ProductPrice price
            where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
              and price.effectiveFrom <= :at
              and (price.effectiveUntil is null or price.effectiveUntil > :at)
            order by price.ownerType, price.currencyCode, price.billingCycle, price.revisionNumber desc, price.id asc
            """)
    List<ProductPrice> findAllApplicable(@Param("at") Instant at);

    @EntityGraph(attributePaths = {"plan"})
    @Query(value = """
            select price from ProductPrice price
            join price.plan plan
            where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
              and price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
              and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ACTIVE
              and price.effectiveFrom <= :at
              and (price.effectiveUntil is null or price.effectiveUntil > :at)
              and (:search is null or lower(plan.code) like lower(concat('%', :search, '%'))
                   or lower(plan.name) like lower(concat('%', :search, '%')))
              and (:currencyCode is null or price.currencyCode = :currencyCode)
              and (:billingCycle is null or price.billingCycle = :billingCycle)
            """,
            countQuery = """
            select count(price) from ProductPrice price
            join price.plan plan
            where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
              and price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
              and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ACTIVE
              and price.effectiveFrom <= :at
              and (price.effectiveUntil is null or price.effectiveUntil > :at)
              and (:search is null or lower(plan.code) like lower(concat('%', :search, '%'))
                   or lower(plan.name) like lower(concat('%', :search, '%')))
              and (:currencyCode is null or price.currencyCode = :currencyCode)
              and (:billingCycle is null or price.billingCycle = :billingCycle)
            """)
    Page<ProductPrice> findAssignablePlanPrices(
            @Param("search") String search,
            @Param("currencyCode") String currencyCode,
            @Param("billingCycle") BillingCycle billingCycle,
            @Param("at") Instant at,
            Pageable pageable);

    @Query("""
            select count(price) from ProductPrice price
            where price.ownerType = :ownerType
              and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
                    and price.plan.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
                    and price.addOn.id = :ownerId)
                or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
                    and price.quotaPackage.id = :ownerId))
              and price.currencyCode = :currencyCode
              and price.billingCycle = :billingCycle
              and price.compatibilityDefault = true
            """)
    long countCompatibilityPrice(@Param("ownerType") ProductPriceOwnerType ownerType,
                                 @Param("ownerId") UUID ownerId,
                                 @Param("currencyCode") String currencyCode,
                                 @Param("billingCycle") BillingCycle billingCycle);

    @Query("select coalesce(max(price.revisionNumber), 0) from ProductPrice price where price.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

    @Query("""
            select price.lineageId, max(price.revisionNumber)
            from ProductPrice price
            where price.lineageId in :lineageIds
            group by price.lineageId
            """)
    List<Object[]> findMaximumRevisionNumbers(@Param("lineageIds") Collection<UUID> lineageIds);

    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    List<ProductPrice> findAllByIdIn(Collection<UUID> ids);

    @Override
    @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
    Page<ProductPrice> findAll(org.springframework.data.jpa.domain.Specification<ProductPrice> specification,
                               Pageable pageable);
}
