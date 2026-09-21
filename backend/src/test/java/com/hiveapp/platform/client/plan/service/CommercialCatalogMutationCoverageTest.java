package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiveapp.platform.client.plan.infrastructure.CommercialCatalogSeeder;
import com.hiveapp.platform.client.plan.infrastructure.PlanSeeder;
import com.hiveapp.platform.client.plan.infrastructure.ProductPriceBackfill;
import com.hiveapp.platform.client.plan.service.impl.CommercialAvailabilityServiceImpl;
import com.hiveapp.platform.client.plan.service.impl.CommercialCampaignAdminServiceImpl;
import com.hiveapp.platform.client.plan.service.impl.CommercialOfferAdminServiceImpl;
import com.hiveapp.platform.client.plan.service.impl.PlanAdminServiceImpl;
import com.hiveapp.platform.client.plan.service.impl.ProductPriceAdminServiceImpl;
import com.hiveapp.platform.registry.service.RegistrySynchronizationCoordinator;
import com.hiveapp.platform.registry.service.impl.RegistryServiceImpl;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class CommercialCatalogMutationCoverageTest {

  @Test
  void everyKnownCommercialWriterIsGuardedAtItsTransactionalBoundary() {
    Map<Class<?>, List<String>> expected =
        Map.ofEntries(
            Map.entry(
                PlanAdminServiceImpl.class,
                List.of(
                    "createPlan",
                    "duplicatePlan",
                    "createPlanVersion", "selectPublicVersion", "updatePlanMetadata",
                    "updatePlan",
                    "transitionStatus",
                    "deletePlan",
                    "assignFeature",
                    "updateFeature",
                    "removeFeature",
                    "createAddOn",
                    "reviseAddOn",
                    "updateAddOn",
                    "transitionAddOnStatus",
                    "deleteAddOn",
                    "assignAddOnFeature",
                    "updateAddOnFeature",
                    "removeAddOnFeature",
                    "createQuotaPackage",
                    "reviseQuotaPackage",
                    "changeQuotaPackageLifecycle",
                    "updateQuotaPackage",
                    "deleteQuotaPackage")),
            Map.entry(
                ProductPriceAdminServiceImpl.class,
                List.of(
                    "createDraft",
                    "updateDraft",
                    "activate",
                    "pause",
                    "reactivate",
                    "revise",
                    "scheduleReplacement",
                    "changePrice",
                    "rescheduleChange",
                    "cancelChange",
                    "archive",
                    "deleteDraft")),
            Map.entry(
                CommercialAvailabilityServiceImpl.class,
                List.of("updatePlan", "updateAddOn", "updateQuotaPackage")),
            Map.entry(
                CommercialCampaignAdminServiceImpl.class,
                List.of(
                    "create",
                    "update",
                    "duplicate",
                    "revise",
                    "schedule",
                    "pause",
                    "resume",
                    "end",
                    "archive",
                    "deleteDraft",
                    "reassignOwner")),
            Map.entry(
                CommercialOfferAdminServiceImpl.class,
                List.of(
                    "create",
                    "update",
                    "duplicate",
                    "revise",
                    "publish",
                    "retire",
                    "restore",
                    "archive",
                    "deleteDraft",
                    "reassignOwner")),
            Map.entry(
                RegistryServiceImpl.class,
                List.of("updatePublicVisibility", "updateNewSales", "updateEmergencyRuntime")),
            Map.entry(RegistrySynchronizationCoordinator.class, List.of("synchronize")),
            Map.entry(PlanSeeder.class, List.of("seed")),
            Map.entry(CommercialCatalogSeeder.class, List.of("seed")),
            Map.entry(ProductPriceBackfill.class, List.of("backfill")));

    expected.forEach(
        (type, expectedMethods) -> {
          List<Method> guarded =
              java.util.Arrays.stream(type.getDeclaredMethods())
                  .filter(method -> method.isAnnotationPresent(CommercialCatalogMutation.class))
                  .toList();

          assertThat(guarded)
              .extracting(Method::getName)
              .containsExactlyInAnyOrderElementsOf(expectedMethods);
          assertThat(guarded)
              .allSatisfy(
                  method ->
                      assertThat(
                              method.isAnnotationPresent(Transactional.class)
                                  || type.isAnnotationPresent(Transactional.class))
                          .as(type.getSimpleName() + "#" + method.getName() + " is transactional")
                          .isTrue());
        });
  }

  @Test
  void conditionalCampaignLifecycleProcessorOwnsItsMutationFenceExplicitly() {
    assertThat(CommercialCampaignLifecycleTransitionService.class.getDeclaredFields())
        .extracting(java.lang.reflect.Field::getType)
        .contains(CommercialCatalogVersionService.class);
    assertThat(
            java.util.Arrays.stream(
                    CommercialCampaignLifecycleTransitionService.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("processDue")))
        .allSatisfy(
            method -> {
              assertThat(method.getReturnType()).isEqualTo(boolean.class);
              assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
              assertThat(method.isAnnotationPresent(CommercialCatalogMutation.class)).isFalse();
            });
  }
}
