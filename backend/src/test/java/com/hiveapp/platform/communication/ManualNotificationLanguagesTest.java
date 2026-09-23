package com.hiveapp.platform.communication;

import static org.assertj.core.api.Assertions.*;

import com.hiveapp.shared.exception.InvalidRequestException;
import java.util.*;
import org.junit.jupiter.api.Test;

class ManualNotificationLanguagesTest {
  private final CommunicationModels.Translation arabic =
      new CommunicationModels.Translation("عنوان", "نص");

  @Test
  void selectsAvailableLanguageAndPreservesOriginalOrLegacyFallback() {
    var value = ManualNotificationLanguages.validated("fr", Map.of("ar", arabic));
    assertThat(
            ManualNotificationLanguages.select(
                    value, "Original", "Texte", Locale.forLanguageTag("ar-MA"))
                .title())
        .isEqualTo("عنوان");
    assertThat(
            ManualNotificationLanguages.select(value, "Original", "Texte", Locale.ENGLISH).title())
        .isEqualTo("Original");
    assertThat(
            ManualNotificationLanguages.select(
                    null, "Legacy", "Unchanged", Locale.forLanguageTag("ar"))
                .body())
        .isEqualTo("Unchanged");
    assertThat(value.snapshot().getTranslations())
        .isEqualTo(value.getTranslations())
        .isNotSameAs(value.getTranslations());
  }

  @Test
  void rejectsUnsupportedDuplicateIncompleteAndOversizedVariants() {
    assertThatThrownBy(() -> ManualNotificationLanguages.validated("en", Map.of()))
        .isInstanceOf(InvalidRequestException.class);
    for (var variants :
        List.of(
            Map.of("fr", arabic),
            Map.of("en", arabic),
            Map.of("ar", new CommunicationModels.Translation("", "text")),
            Map.of("ar", new CommunicationModels.Translation("title", "a".repeat(10001)))))
      assertThatThrownBy(() -> ManualNotificationLanguages.validated("fr", variants))
          .isInstanceOf(InvalidRequestException.class);
  }
}
