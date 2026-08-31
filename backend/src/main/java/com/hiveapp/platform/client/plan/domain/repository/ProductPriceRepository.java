package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductPriceRepository
    extends JpaRepository<ProductPrice, UUID>, JpaSpecificationExecutor<ProductPrice> {

  @Override
  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  Optional<ProductPrice> findById(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query("select price from ProductPrice price where price.id = :priceId")
  Optional<ProductPrice> findByIdForUpdate(@Param("priceId") UUID priceId);

  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query(
      """
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
  Optional<ProductPrice> findOwned(
      @Param("ownerType") ProductPriceOwnerType ownerType,
      @Param("ownerId") UUID ownerId,
      @Param("priceId") UUID priceId);

  @Query(
      """
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
  and not exists (select plan.id from Plan plan
        where plan = price.plan
          and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ARCHIVED)
  and not exists (select addOn.id from AddOn addOn
        where addOn = price.addOn
          and addOn.status = com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ARCHIVED)
  and not exists (select item.id from QuotaPackage item
        where item = price.quotaPackage
          and item.status = com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ARCHIVED)
  and price.currencyCode = :currencyCode
  and price.billingCycle = :billingCycle
  and (:effectiveUntil is null or price.effectiveFrom < :effectiveUntil)
  and (price.effectiveUntil is null or price.effectiveUntil > :effectiveFrom)
""")
  long countActiveOverlaps(
      @Param("ownerType") ProductPriceOwnerType ownerType,
      @Param("ownerId") UUID ownerId,
      @Param("currencyCode") String currencyCode,
      @Param("billingCycle") BillingCycle billingCycle,
      @Param("effectiveFrom") Instant effectiveFrom,
      @Param("effectiveUntil") Instant effectiveUntil,
      @Param("excludedId") UUID excludedId);

  @Query(
      """
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
  @Query(
      """
select price from ProductPrice price
where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and price.ownerType = :ownerType
  and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
        and price.plan.id = :ownerId)
    or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
        and price.addOn.id = :ownerId)
    or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
        and price.quotaPackage.id = :ownerId))
  and not exists (select plan.id from Plan plan
        where plan = price.plan
          and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ARCHIVED)
  and not exists (select addOn.id from AddOn addOn
        where addOn = price.addOn
          and addOn.status = com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ARCHIVED)
  and not exists (select item.id from QuotaPackage item
        where item = price.quotaPackage
          and item.status = com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ARCHIVED)
  and price.currencyCode = :currencyCode
  and price.billingCycle = :billingCycle
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
order by price.revisionNumber desc, price.id asc
""")
  List<ProductPrice> findApplicable(
      @Param("ownerType") ProductPriceOwnerType ownerType,
      @Param("ownerId") UUID ownerId,
      @Param("currencyCode") String currencyCode,
      @Param("billingCycle") BillingCycle billingCycle,
      @Param("at") Instant at);

  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query(
      """
select price from ProductPrice price
where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and price.ownerType = :ownerType
  and ((:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
        and price.plan.id = :ownerId)
    or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
        and price.addOn.id = :ownerId)
    or (:ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
        and price.quotaPackage.id = :ownerId))
  and not exists (select plan.id from Plan plan
        where plan = price.plan
          and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ARCHIVED)
  and not exists (select addOn.id from AddOn addOn
        where addOn = price.addOn
          and addOn.status = com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ARCHIVED)
  and not exists (select item.id from QuotaPackage item
        where item = price.quotaPackage
          and item.status = com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ARCHIVED)
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
order by price.currencyCode, price.billingCycle, price.revisionNumber desc, price.id asc
""")
  List<ProductPrice> findAllApplicable(
      @Param("ownerType") ProductPriceOwnerType ownerType,
      @Param("ownerId") UUID ownerId,
      @Param("at") Instant at);

  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query(
      """
select price from ProductPrice price
where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and not exists (select plan.id from Plan plan
        where plan = price.plan
          and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ARCHIVED)
  and not exists (select addOn.id from AddOn addOn
        where addOn = price.addOn
          and addOn.status = com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ARCHIVED)
  and not exists (select item.id from QuotaPackage item
        where item = price.quotaPackage
          and item.status = com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ARCHIVED)
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
order by price.ownerType, price.currencyCode, price.billingCycle, price.revisionNumber desc, price.id asc
""")
  List<ProductPrice> findAllApplicable(@Param("at") Instant at);

  @EntityGraph(attributePaths = {"plan"})
  @Query(
      value =
          """
select price from ProductPrice price
join price.plan plan
where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
  and price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ACTIVE
  and not exists (
    select planFeature.id from PlanFeature planFeature
    join planFeature.feature feature
    where planFeature.plan = plan
      and planFeature.mode = com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode.INCLUDED
      and (
        feature.code not in :staticallyEligibleFeatureCodes
        or feature.newSalesEnabled = false
        or feature.runtimeEnabled = false
        or feature.status not in (
          com.hiveapp.platform.registry.domain.constant.FeatureStatus.PUBLIC,
          com.hiveapp.platform.registry.domain.constant.FeatureStatus.BETA
        )
      )
  )
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
  and (:search is null or lower(plan.code) like lower(concat('%', :search, '%'))
       or lower(plan.name) like lower(concat('%', :search, '%')))
  and (:currencyCode is null or price.currencyCode = :currencyCode)
  and (:billingCycle is null or price.billingCycle = :billingCycle)
""",
      countQuery =
          """
select count(price) from ProductPrice price
join price.plan plan
where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
  and price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ACTIVE
  and not exists (
    select planFeature.id from PlanFeature planFeature
    join planFeature.feature feature
    where planFeature.plan = plan
      and planFeature.mode = com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode.INCLUDED
      and (
        feature.code not in :staticallyEligibleFeatureCodes
        or feature.newSalesEnabled = false
        or feature.runtimeEnabled = false
        or feature.status not in (
          com.hiveapp.platform.registry.domain.constant.FeatureStatus.PUBLIC,
          com.hiveapp.platform.registry.domain.constant.FeatureStatus.BETA
        )
      )
  )
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
  and (:search is null or lower(plan.code) like lower(concat('%', :search, '%'))
       or lower(plan.name) like lower(concat('%', :search, '%')))
  and (:currencyCode is null or price.currencyCode = :currencyCode)
  and (:billingCycle is null or price.billingCycle = :billingCycle)
""")
  Page<ProductPrice> findAssignablePlanPrices(
      @Param("staticallyEligibleFeatureCodes") Collection<String> staticallyEligibleFeatureCodes,
      @Param("search") String search,
      @Param("currencyCode") String currencyCode,
      @Param("billingCycle") BillingCycle billingCycle,
      @Param("at") Instant at,
      Pageable pageable);

  @Query(
      """
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
  long countCompatibilityPrice(
      @Param("ownerType") ProductPriceOwnerType ownerType,
      @Param("ownerId") UUID ownerId,
      @Param("currencyCode") String currencyCode,
      @Param("billingCycle") BillingCycle billingCycle);

  @Query(
      "select coalesce(max(price.revisionNumber), 0) from ProductPrice price where price.lineageId"
          + " = :lineageId")
  int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

  @Query(
      """
      select price.lineageId, max(price.revisionNumber)
      from ProductPrice price
      where price.lineageId in :lineageIds
      group by price.lineageId
      """)
  List<Object[]> findMaximumRevisionNumbers(@Param("lineageIds") Collection<UUID> lineageIds);

  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage", "quotaPackage.feature"})
  List<ProductPrice> findAllByIdIn(Collection<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query("select price from ProductPrice price where price.id in :ids order by price.id")
  List<ProductPrice> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);

  @Override
  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage", "quotaPackage.feature"})
  Page<ProductPrice> findAll(
      org.springframework.data.jpa.domain.Specification<ProductPrice> specification,
      Pageable pageable);

  @Query(
      """
select price.plan.id, count(price)
from ProductPrice price
where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
  and price.plan.id in :ownerIds
  and price.plan.status <> com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ARCHIVED
  and price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
group by price.plan.id
""")
  List<Object[]> countApplicablePlanPrices(
      @Param("ownerIds") Collection<UUID> ownerIds, @Param("at") Instant at);

  @Query(
      "select price.plan.id, count(price) from ProductPrice price "
          + "where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN "
          + "and price.plan.id in :ownerIds and price.status = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT "
          + "group by price.plan.id")
  List<Object[]> countDraftPlanPrices(@Param("ownerIds") Collection<UUID> ownerIds);

  @Query(
      "select price.plan.id, count(price) from ProductPrice price "
          + "where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN "
          + "and price.plan.id in :ownerIds and price.status <> "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT "
          + "group by price.plan.id")
  List<Object[]> countPublishedPlanPrices(@Param("ownerIds") Collection<UUID> ownerIds);

  @Query(
      """
select price.addOn.id, count(price)
from ProductPrice price
where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
  and price.addOn.id in :ownerIds
  and price.addOn.status <> com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ARCHIVED
  and price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
group by price.addOn.id
""")
  List<Object[]> countApplicableAddOnPrices(
      @Param("ownerIds") Collection<UUID> ownerIds, @Param("at") Instant at);

  @Query(
      "select price.addOn.id, count(price) from ProductPrice price "
          + "where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON "
          + "and price.addOn.id in :ownerIds and price.status = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT "
          + "group by price.addOn.id")
  List<Object[]> countDraftAddOnPrices(@Param("ownerIds") Collection<UUID> ownerIds);

  @Query(
      "select price.addOn.id, count(price) from ProductPrice price "
          + "where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON "
          + "and price.addOn.id in :ownerIds and price.status <> "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT "
          + "group by price.addOn.id")
  List<Object[]> countPublishedAddOnPrices(@Param("ownerIds") Collection<UUID> ownerIds);

  @Query(
      """
select price.quotaPackage.id, count(price)
from ProductPrice price
where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
  and price.quotaPackage.id in :ownerIds
  and price.quotaPackage.status <> com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ARCHIVED
  and price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
group by price.quotaPackage.id
""")
  List<Object[]> countApplicableQuotaPackagePrices(
      @Param("ownerIds") Collection<UUID> ownerIds, @Param("at") Instant at);

  @Query(
      "select price.quotaPackage.id, count(price) from ProductPrice price "
          + "where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE "
          + "and price.quotaPackage.id in :ownerIds and price.status = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT "
          + "group by price.quotaPackage.id")
  List<Object[]> countDraftQuotaPackagePrices(@Param("ownerIds") Collection<UUID> ownerIds);

  @Query(
      "select price.quotaPackage.id, count(price) from ProductPrice price "
          + "where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE "
          + "and price.quotaPackage.id in :ownerIds and price.status = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT "
          + "and price.effectiveFrom <= :at "
          + "and (price.effectiveUntil is null or price.effectiveUntil > :at) "
          + "group by price.quotaPackage.id")
  List<Object[]> countCurrentlyApplicableDraftQuotaPackagePrices(
      @Param("ownerIds") Collection<UUID> ownerIds, @Param("at") Instant at);

  @Query(
      "select price.quotaPackage.id, count(price) from ProductPrice price "
          + "where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE "
          + "and price.quotaPackage.id in :ownerIds and price.status <> "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.DRAFT "
          + "group by price.quotaPackage.id")
  List<Object[]> countPublishedQuotaPackagePrices(@Param("ownerIds") Collection<UUID> ownerIds);

  @EntityGraph(attributePaths = {"quotaPackage", "sourcePrice"})
  @Query(
      """
select price from ProductPrice price
where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
  and price.quotaPackage.id = :quotaPackageId
order by price.currencyCode, price.billingCycle, price.effectiveFrom desc,
         price.revisionNumber desc, price.id
""")
  List<ProductPrice> findAllByQuotaPackageId(@Param("quotaPackageId") UUID quotaPackageId);

  @EntityGraph(attributePaths = {"plan", "sourcePrice"})
  @Query(
      "select price from ProductPrice price where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN "
          + "and price.plan.id = :planId order by price.id")
  List<ProductPrice> findAllByPlanId(@Param("planId") UUID planId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"plan", "sourcePrice"})
  @Query(
      "select price from ProductPrice price where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN "
          + "and price.plan.id = :planId order by price.id")
  List<ProductPrice> findAllByPlanIdForUpdate(@Param("planId") UUID planId);

  @EntityGraph(attributePaths = {"addOn", "sourcePrice"})
  @Query(
      "select price from ProductPrice price where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON "
          + "and price.addOn.id = :addOnId order by price.id")
  List<ProductPrice> findAllByAddOnId(@Param("addOnId") UUID addOnId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"addOn", "sourcePrice"})
  @Query(
      "select price from ProductPrice price where price.ownerType = "
          + "com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON "
          + "and price.addOn.id = :addOnId order by price.id")
  List<ProductPrice> findAllByAddOnIdForUpdate(@Param("addOnId") UUID addOnId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"quotaPackage", "sourcePrice"})
  @Query(
      """
select price from ProductPrice price
where price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
  and price.quotaPackage.id = :quotaPackageId
order by price.id
""")
  List<ProductPrice> findAllByQuotaPackageIdForUpdate(@Param("quotaPackageId") UUID quotaPackageId);

  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query(
      """
select price from ProductPrice price
where (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
        and price.plan.id in :planIds)
   or (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
        and price.addOn.id in :addOnIds)
order by price.id
""")
  List<ProductPrice> findAllCompatibilityDependencyPrices(
      @Param("planIds") Collection<UUID> planIds, @Param("addOnIds") Collection<UUID> addOnIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query(
      """
select price from ProductPrice price
where (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
        and price.plan.id in :planIds)
   or (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
        and price.addOn.id in :addOnIds)
order by price.id
""")
  List<ProductPrice> findAllCompatibilityDependencyPricesForUpdate(
      @Param("planIds") Collection<UUID> planIds, @Param("addOnIds") Collection<UUID> addOnIds);

  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query(
      """
select price from ProductPrice price
where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
  and ((price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
        and price.plan.id in :planIds)
    or (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
        and price.addOn.id in :addOnIds)
    or (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
        and price.quotaPackage.id in :quotaPackageIds))
  and not exists (select plan.id from Plan plan
        where plan = price.plan
          and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ARCHIVED)
  and not exists (select addOn.id from AddOn addOn
        where addOn = price.addOn
          and addOn.status = com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ARCHIVED)
  and not exists (select item.id from QuotaPackage item
        where item = price.quotaPackage
          and item.status = com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ARCHIVED)
order by price.id
""")
  List<ProductPrice> findAllApplicableForOwners(
      @Param("planIds") Collection<UUID> planIds,
      @Param("addOnIds") Collection<UUID> addOnIds,
      @Param("quotaPackageIds") Collection<UUID> quotaPackageIds,
      @Param("at") Instant at);

  @EntityGraph(attributePaths = {"plan", "addOn", "quotaPackage"})
  @Query(
      """
select price from ProductPrice price
where price.status = com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus.ACTIVE
  and price.effectiveFrom <= :at
  and (price.effectiveUntil is null or price.effectiveUntil > :at)
  and ((price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.PLAN
        and price.plan.id in :planIds)
    or (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.ADD_ON
        and price.addOn.id in :addOnIds)
    or (price.ownerType = com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType.QUOTA_PACKAGE
        and price.quotaPackage.id in :quotaPackageIds))
  and not exists (select plan.id from Plan plan
        where plan = price.plan
          and plan.status = com.hiveapp.platform.client.plan.domain.constant.PlanStatus.ARCHIVED)
  and not exists (select addOn.id from AddOn addOn
        where addOn = price.addOn
          and addOn.status = com.hiveapp.platform.client.plan.domain.constant.AddOnStatus.ARCHIVED)
  and not exists (select item.id from QuotaPackage item
        where item = price.quotaPackage
          and item.status = com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus.ARCHIVED)
order by price.id
""")
  List<ProductPrice> findAllApplicableForOwnersBounded(
      @Param("planIds") Collection<UUID> planIds,
      @Param("addOnIds") Collection<UUID> addOnIds,
      @Param("quotaPackageIds") Collection<UUID> quotaPackageIds,
      @Param("at") Instant at,
      Pageable pageable);
}
