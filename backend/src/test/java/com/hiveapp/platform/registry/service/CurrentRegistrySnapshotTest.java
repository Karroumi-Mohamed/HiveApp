package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class CurrentRegistrySnapshotTest {
    @Test
    void lookupFailsClosedBeforeInstallAndReusesAnImmutableIndexAfterInstall() {
        var current = new CurrentRegistrySnapshot();
        assertThat(current.view().definitionFor("platform.plans.read")).isNull();
        assertThat(current.view().definitionFor(null)).isNull();
        current.install(snapshot("plans"));
        var index = current.view().definitionsByCode();
        for (int i = 0; i < 1_000; i++) {
            assertThat(current.view().definitionsByCode()).isSameAs(index);
            assertThat(current.view().definitionFor("platform.plans.read")).isSameAs(index.get("platform.plans"));
        }
        assertThat(current.view().definitionFor("platform.plans.removed")).isNull();
        assertThatThrownBy(() -> index.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void replacementRemovesOldActionsAndFailedInstallLeavesPreviousSnapshotIntact() {
        var current = new CurrentRegistrySnapshot();
        current.install(snapshot("plans"));
        var previous = current.view();
        var definition = previous.definitionsByCode().get("platform.plans");
        assertThatThrownBy(() -> current.install(new RegistrySnapshot(
                List.of(definition, definition), List.of(), Set.of("platform.plans.read"), "invalid")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(current.view()).isSameAs(previous);
        current.install(snapshot("roles"));
        assertThat(current.view().definitionFor("platform.plans.read")).isNull();
        assertThat(current.view().definitionFor("platform.roles.read")).isNotNull();
        assertThat(previous.definitionFor("platform.roles.read")).isNull();
    }

    @Test
    void concurrentReadersNeverMixDefinitionsAndActionsFromDifferentBuilds() throws Exception {
        var current = new CurrentRegistrySnapshot();
        var plans = snapshot("plans");
        var roles = snapshot("roles");
        current.install(plans);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var writer = executor.submit(() -> {
                start.await();
                for (int i = 0; i < 1_000; i++) current.install(i % 2 == 0 ? plans : roles);
                return true;
            });
            java.util.concurrent.Callable<Boolean> read = () -> {
                start.await();
                for (int i = 0; i < 5_000; i++) {
                    var view = current.view();
                    assertThat(view.definitionFor("platform." + view.hash() + ".read")).isNotNull();
                    assertThat(view.definitionsByCode()).hasSize(1);
                    assertThat(view.actionCodes()).containsExactly("platform." + view.hash() + ".read");
                }
                return true;
            };
            var first = executor.submit(read);
            var second = executor.submit(read);
            start.countDown();
            assertThat(writer.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RegistrySnapshot snapshot(String feature) {
        return new RegistrySnapshot(List.of(FeatureDefinition.platformControl("platform." + feature)
                .displayName(feature).build()), List.of(), Set.of("platform." + feature + ".read"), feature);
    }
}
