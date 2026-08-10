package com.hiveapp.platform.registry.service.impl;

import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.PlansFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.constant.FeatureOperationalControl;
import com.hiveapp.platform.registry.domain.constant.RegistrySyncStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncRun;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncLock;
import com.hiveapp.platform.registry.domain.entity.FeatureOperationalChange;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import com.hiveapp.platform.registry.domain.repository.FeatureOperationalChangeRepository;
import com.hiveapp.platform.registry.dto.FeatureCatalogAudience;
import com.hiveapp.platform.registry.dto.PermissionCatalogAudience;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import com.hiveapp.platform.registry.service.RegistrySnapshot;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RegistryServiceImplTest {

    @Mock private FeatureRepository featureRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RegistrySyncRunRepository registrySyncRunRepository;
    @Mock private FeatureOperationalChangeRepository operationalChangeRepository;
    @Mock private RegistryCatalogVersionService catalogVersionService;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void planAssignableCatalogExposesOnlyPlanAssignableWorkspaceFeatures() {
        RegistryServiceImpl service = service();
        when(featureRepository.findAll()).thenReturn(List.of(
                feature(CompanyFeature.CODE, FeatureStatus.PUBLIC),
                feature(PlansFeature.CODE, FeatureStatus.INTERNAL)
        ));
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission("platform.company.create"),
                permission("platform.plans.create")
        ));

        var catalog = service.getFeatureCatalog(FeatureCatalogAudience.PLAN_ASSIGNABLE);

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).features())
                .extracting(feature -> feature.code())
                .containsExactly(CompanyFeature.CODE);
        assertThat(catalog.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly("platform.company.create");
        assertThat(catalog.get(0).features().get(0).publicVisibilityToggleable()).isTrue();
    }

    @Test
    void platformAdminPermissionCatalogExposesOnlyControlPlanePermissions() {
        RegistryServiceImpl service = service();
        when(featureRepository.findAll()).thenReturn(List.of(
                feature(CompanyFeature.CODE, FeatureStatus.PUBLIC),
                feature(PlansFeature.CODE, FeatureStatus.INTERNAL)
        ));
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission("platform.company.create"),
                permission("platform.plans.create")
        ));

        var catalog = service.getPermissionCatalog(PermissionCatalogAudience.PLATFORM_ADMIN_ROLE_GRANTABLE);

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).features())
                .extracting(feature -> feature.code())
                .containsExactly(PlansFeature.CODE);
        assertThat(catalog.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly("platform.plans.create");
    }

    @Test
    void permissionCatalogExcludesStaleDatabaseActionsAbsentFromCurrentSnapshot() {
        RegistryServiceImpl service = service();
        when(featureRepository.findAll()).thenReturn(List.of(
                feature(CompanyFeature.CODE, FeatureStatus.PUBLIC)));
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission("platform.company.create"),
                permission("platform.company.removed_action")));

        var catalog = service.getPermissionCatalog(PermissionCatalogAudience.CLIENT_ROLE_GRANTABLE);

        assertThat(catalog.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly("platform.company.create");
    }

    @Test
    void b2bPermissionCatalogExposesOnlyExplicitDelegatableActions() {
        RegistryServiceImpl service = service();
        when(featureRepository.findAll()).thenReturn(List.of(feature(CompanyFeature.CODE, FeatureStatus.PUBLIC)));
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission("platform.company.create"),
                permission("platform.company.read_single"),
                permission("platform.company.delete")
        ));

        var catalog = service.getPermissionCatalog(PermissionCatalogAudience.B2B_DELEGATABLE);

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).features()).hasSize(1);
        assertThat(catalog.get(0).features().get(0).permissions())
                .extracting(permission -> permission.code())
                .containsExactly("platform.company.read_single");
    }

    @Test
    void publicVisibilityChangeDoesNotAlterSaleGrantOrRuntimeControls() {
        RegistryServiceImpl service = service(List.of(CompanyFeature.definition()));
        UUID featureId = UUID.randomUUID();
        Feature feature = feature(CompanyFeature.CODE, FeatureStatus.PUBLIC);
        RegistrySyncLock catalogLock = new RegistrySyncLock();
        when(catalogVersionService.lockForMutation()).thenReturn(catalogLock);
        when(featureRepository.findByIdForUpdate(featureId)).thenReturn(Optional.of(feature));
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                UUID.randomUUID(), null, null, null, null, false));

        service.updatePublicVisibility(featureId, false, "Temporarily hide marketing listing");

        assertThat(feature.isPublicVisible()).isFalse();
        assertThat(feature.isNewSalesEnabled()).isTrue();
        assertThat(feature.isNewGrantsEnabled()).isTrue();
        assertThat(feature.isRuntimeEnabled()).isTrue();
        verify(featureRepository).save(feature);
        verify(operationalChangeRepository).save(org.mockito.ArgumentMatchers.any(FeatureOperationalChange.class));
        verify(catalogVersionService).bump(catalogLock);
    }

    @Test
    void salesControlRejectsNonSellableControlPlaneFeature() {
        RegistryServiceImpl service = service(List.of(PlansFeature.definition()));
        UUID featureId = UUID.randomUUID();
        Feature feature = feature(PlansFeature.CODE, FeatureStatus.INTERNAL);
        when(catalogVersionService.lockForMutation()).thenReturn(new RegistrySyncLock());
        when(featureRepository.findByIdForUpdate(featureId)).thenReturn(Optional.of(feature));

        assertThatThrownBy(() -> service.updateNewSales(featureId, false, "Not sellable"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not expose the NEW_SALES operator control");
        verifyNoInteractions(operationalChangeRepository);
    }

    @Test
    void emergencyRuntimeChangeRequiresBothExplicitConfirmations() {
        RegistryServiceImpl service = service(List.of(CompanyFeature.definition()));
        assertThatThrownBy(() -> service.updateEmergencyRuntime(
                UUID.randomUUID(), false, "Incident", true, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("impact and communication confirmation");
        verifyNoInteractions(featureRepository, operationalChangeRepository, catalogVersionService);
    }

    @Test
    void controlHistoryReturnsTypedDurableAuditEntries() {
        RegistryServiceImpl service = service(List.of(CompanyFeature.definition()));
        UUID featureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Feature feature = feature(CompanyFeature.CODE, FeatureStatus.PUBLIC);
        FeatureOperationalChange change = new FeatureOperationalChange();
        change.setFeature(feature);
        change.setControl(FeatureOperationalControl.EMERGENCY_RUNTIME);
        change.setActorUserId(actorId);
        change.setPreviousValue(true);
        change.setNewValue(false);
        change.setReason("Incident containment");
        change.setImpactConfirmed(true);
        change.setCommunicationConfirmed(true);

        when(featureRepository.findById(featureId)).thenReturn(Optional.of(feature));
        when(operationalChangeRepository.findAllByFeatureIdOrderByCreatedAtDesc(featureId))
                .thenReturn(List.of(change));

        var history = service.getFeatureControlHistory(featureId);

        assertThat(history).singleElement().satisfies(entry -> {
            assertThat(entry.featureCode()).isEqualTo(CompanyFeature.CODE);
            assertThat(entry.control()).isEqualTo(FeatureOperationalControl.EMERGENCY_RUNTIME);
            assertThat(entry.actorUserId()).isEqualTo(actorId);
            assertThat(entry.previousValue()).isTrue();
            assertThat(entry.newValue()).isFalse();
            assertThat(entry.reason()).isEqualTo("Incident containment");
            assertThat(entry.impactConfirmed()).isTrue();
            assertThat(entry.communicationConfirmed()).isTrue();
            assertThat(entry.effectiveTiming()).isEqualTo("IMMEDIATE");
        });
    }

    @Test
    void latestSynchronizationReturnsAdminSafePersistentSummary() {
        RegistryServiceImpl service = service();
        RegistrySyncRun run = new RegistrySyncRun();
        run.setBuildVersion("build-42");
        run.setSnapshotHash("hash");
        run.setStatus(RegistrySyncStatus.SUCCEEDED);
        run.setStartedAt(Instant.parse("2026-07-17T00:00:00Z"));
        run.setCompletedAt(Instant.parse("2026-07-17T00:00:01Z"));
        run.setDiscoveredModules(1);
        run.setDiscoveredFeatures(12);
        run.setDiscoveredPermissions(96);
        run.setOrphanedPermissions(2);
        run.setDetails("Authoritative registry snapshot synchronized");
        when(registrySyncRunRepository.findFirstByOrderByStartedAtDesc())
                .thenReturn(Optional.of(run));

        var result = service.getLatestSynchronizationRun();

        assertThat(result.buildVersion()).isEqualTo("build-42");
        assertThat(result.snapshotHash()).isEqualTo("hash");
        assertThat(result.status()).isEqualTo(RegistrySyncStatus.SUCCEEDED);
        assertThat(result.discoveredFeatures()).isEqualTo(12);
        assertThat(result.discoveredPermissions()).isEqualTo(96);
        assertThat(result.orphanedPermissions()).isEqualTo(2);
    }

    private RegistryServiceImpl service() {
        return service(List.of(CompanyFeature.definition(), PlansFeature.definition()));
    }

    private RegistryServiceImpl service(List<FeatureDefinition> definitions) {
        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(List.of(() -> definitions));
        return new RegistryServiceImpl(
                featureRepository,
                permissionRepository,
                registrySyncRunRepository,
                provider(collector),
                currentSnapshot(definitions),
                operationalChangeRepository,
                catalogVersionService);
    }

    private static ObjectProvider<FeatureDefinitionCollector> provider(FeatureDefinitionCollector collector) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("featureDefinitionCollector", collector);
        return beanFactory.getBeanProvider(FeatureDefinitionCollector.class);
    }

    private static CurrentRegistrySnapshot currentSnapshot(List<FeatureDefinition> definitions) {
        CurrentRegistrySnapshot current = new CurrentRegistrySnapshot();
        current.install(new RegistrySnapshot(
                definitions,
                List.of(),
                java.util.Set.of(
                        "platform.company.create",
                        "platform.company.read_single",
                        "platform.company.delete",
                        "platform.plans.create"),
                "test"));
        return current;
    }

    private static Feature feature(String code, FeatureStatus status) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setStatus(status);
        feature.setPublicVisible(true);
        feature.setNewSalesEnabled(true);
        feature.setNewGrantsEnabled(true);
        feature.setRuntimeEnabled(true);
        return feature;
    }

    private static Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(code);
        permission.setAction(code.substring(code.lastIndexOf('.') + 1));
        permission.setResource(code.substring(0, code.lastIndexOf('.')));
        return permission;
    }
}
