package com.hiveapp.shared.quota;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaLimitEntryTest {

    @Test
    void finiteZeroIsDistinctFromUnlimited() {
        assertThat(new QuotaLimitEntry("members", 0L).mode()).isEqualTo(QuotaLimitMode.FINITE);
        assertThat(QuotaLimitEntry.unlimited("members").mode()).isEqualTo(QuotaLimitMode.UNLIMITED);
    }

    @Test
    void finiteModeRequiresANonNegativeLimit() {
        assertThatThrownBy(() -> new QuotaLimitEntry("members", QuotaLimitMode.FINITE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuotaLimitEntry("members", QuotaLimitMode.FINITE, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unlimitedModeRejectsAFiniteValue() {
        assertThatThrownBy(() -> new QuotaLimitEntry("members", QuotaLimitMode.UNLIMITED, 5L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
