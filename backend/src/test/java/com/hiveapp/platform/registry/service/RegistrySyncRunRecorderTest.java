package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.constant.RegistrySyncStatus;
import com.hiveapp.platform.registry.domain.entity.RegistrySyncRun;
import com.hiveapp.platform.registry.domain.repository.RegistrySyncRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrySyncRunRecorderTest {

    @Mock private RegistrySyncRunRepository runRepository;

    @Test
    void failureReportIsPersistedSeparatelyWithBoundedSafeDetails() {
        RegistrySyncRunRecorder recorder = new RegistrySyncRunRecorder(runRepository);
        RuntimeException failure = new IllegalStateException("x".repeat(3000));

        recorder.recordFailure("build", null, Instant.now(), failure);

        ArgumentCaptor<RegistrySyncRun> run = ArgumentCaptor.forClass(RegistrySyncRun.class);
        verify(runRepository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(RegistrySyncStatus.FAILED);
        assertThat(run.getValue().getBuildVersion()).isEqualTo("build");
        assertThat(run.getValue().getSnapshotHash()).isNull();
        assertThat(run.getValue().getDetails())
                .hasSize(2000)
                .startsWith("IllegalStateException:");
    }
}
