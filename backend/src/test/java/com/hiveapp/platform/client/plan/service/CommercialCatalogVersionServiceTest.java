package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.CommercialCatalogRevision;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCatalogRevisionRepository;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommercialCatalogVersionServiceTest {

    private final CommercialCatalogRevisionRepository repository =
            mock(CommercialCatalogRevisionRepository.class);
    private final CommercialCatalogVersionService service =
            new CommercialCatalogVersionService(repository);

    @Test
    void consistentReadReturnsTheValueWithItsStartingRevision() {
        when(repository.findRevision(CommercialCatalogVersionService.LOCK_NAME))
                .thenReturn(Optional.of(7L), Optional.of(7L));

        String result = service.readConsistently(revision -> "value@" + revision);

        assertThat(result).isEqualTo("value@7");
    }

    @Test
    void consistentReadRejectsAnInterleavedCommittedMutation() {
        when(repository.findRevision(CommercialCatalogVersionService.LOCK_NAME))
                .thenReturn(Optional.of(7L), Optional.of(8L));

        assertThatThrownBy(() -> service.readConsistently(revision -> "value"))
                .isInstanceOf(StaleResourceVersionException.class)
                .hasMessageContaining("changed during review");
    }

    @Test
    void consistentReadCanExposeACallerSpecificStaleContract() {
        when(repository.findRevision(CommercialCatalogVersionService.LOCK_NAME))
                .thenReturn(Optional.of(7L), Optional.of(8L));

        assertThatThrownBy(() -> service.readConsistently(
                revision -> "value",
                TestPreviewConflict::new))
                .isInstanceOf(TestPreviewConflict.class);
    }

    @Test
    void lockAndBumpUseTheSingletonRow() {
        UUID id = UUID.randomUUID();
        CommercialCatalogRevision revision = mock(CommercialCatalogRevision.class);
        when(revision.getId()).thenReturn(id);
        when(repository.findByLockNameForUpdate(CommercialCatalogVersionService.LOCK_NAME))
                .thenReturn(Optional.of(revision));
        when(repository.incrementRevision(id)).thenReturn(1);

        assertThat(service.lockForMutation()).isSameAs(revision);
        service.bump(id);

        verify(repository).incrementRevision(id);
    }

    private static final class TestPreviewConflict extends RuntimeException {}
}
