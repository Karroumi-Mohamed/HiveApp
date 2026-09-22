package com.hiveapp.platform.communication;

import static org.assertj.core.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class NotificationTextTest {
  @Test
  void everyAutomaticContractHasFrenchAndArabicCopyWhileManualContentIsUntouched() {
    for (var type : CoreNotification.values()) {
      if (type == CoreNotification.INTERNAL_INFORMATION
          || type == CoreNotification.OFFER_AVAILABLE) {
        assertThat(type.content(Locale.FRENCH)).isNull();
        continue;
      }
      var french = type.content(Locale.FRENCH);
      var arabic = type.content(Locale.forLanguageTag("ar-MA"));
      assertThat(french.title()).isNotBlank();
      assertThat(french.body()).isNotBlank();
      assertThat(arabic.title()).isNotBlank().isNotEqualTo(french.title());
      assertThat(arabic.body()).isNotBlank().isNotEqualTo(french.body());
      assertThat(type.content(Locale.JAPANESE)).isEqualTo(french);
    }
  }
}
