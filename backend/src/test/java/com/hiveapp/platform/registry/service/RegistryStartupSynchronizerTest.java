package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistryStartupSynchronizerTest {

    @Mock private RegistrySnapshotFactory snapshotFactory;
    @Mock private RegistrySynchronizationCoordinator coordinator;
    @Mock private RegistrySyncRunRecorder runRecorder;

    @Test
    void discoveryFailureIsRecordedAndNeverInstalledAsCurrent() {
        CurrentRegistrySnapshot current = new CurrentRegistrySnapshot();
        RegistryStartupSynchronizer synchronizer =
                new RegistryStartupSynchronizer(snapshotFactory, coordinator, runRecorder, current);
        ReflectionTestUtils.setField(synchronizer, "buildVersion", "test-build");
        FeatureDefinitionException failure =
                new FeatureDefinitionException("Permissionizer discovery returned no permissions");
        when(snapshotFactory.discoverAndValidate()).thenThrow(failure);

        assertThatThrownBy(synchronizer::synchronize).isSameAs(failure);

        verify(runRecorder).recordFailure(eq("test-build"), eq(null), any(), eq(failure));
        verifyNoInteractions(coordinator);
        assertThat(current.actionCodes()).isEmpty();
    }
}
