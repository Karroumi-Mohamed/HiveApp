package com.hiveapp.platform.communication;

import com.hiveapp.shared.exception.InvalidRequestException;
import jakarta.persistence.*;
import java.util.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Authored alternatives to one original message, never separate notifications or machine
 * translation.
 */
@Embeddable
@Getter
@NoArgsConstructor
public class ManualNotificationLanguages {
  @Column(name = "original_language", length = 5)
  private String originalLanguage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "language_variants")
  private Map<String, CommunicationModels.Translation> translations;

  public static ManualNotificationLanguages validated(
      String original, Map<String, CommunicationModels.Translation> variants) {
    var result = new ManualNotificationLanguages();
    result.originalLanguage = original == null ? "fr" : original;
    if (!Set.of("fr", "ar").contains(result.originalLanguage))
      throw new InvalidRequestException("Choose French or Arabic for the original message.");
    if (variants != null && variants.size() > 1)
      throw new InvalidRequestException("Only one alternative language is supported.");
    var clean = new TreeMap<String, CommunicationModels.Translation>();
    if (variants != null)
      variants.forEach(
          (language, content) -> {
            if (language == null
                || !Set.of("fr", "ar").contains(language)
                || language.equals(result.originalLanguage)
                || content == null
                || content.messageTitle() == null
                || content.messageTitle().isBlank()
                || content.messageTitle().length() > 160
                || content.messageBody() == null
                || content.messageBody().isBlank()
                || content.messageBody().length() > 10000)
              throw new InvalidRequestException(
                  "Each translation needs a distinct supported language and a complete"
                      + " title/message.");
            clean.put(
                language,
                new CommunicationModels.Translation(
                    content.messageTitle().trim(), content.messageBody().trim()));
          });
    result.translations = clean;
    return result;
  }

  public static String original(ManualNotificationLanguages value) {
    return value == null ? null : value.originalLanguage;
  }

  public static Map<String, CommunicationModels.Translation> variants(
      ManualNotificationLanguages value) {
    return value == null || value.translations == null
        ? Map.of()
        : Collections.unmodifiableMap(value.translations);
  }

  public static NotificationText.Content select(
      ManualNotificationLanguages value, String title, String body, Locale locale) {
    var translated = variants(value).get(locale.getLanguage());
    return translated == null
        ? new NotificationText.Content(title, body)
        : new NotificationText.Content(translated.messageTitle(), translated.messageBody());
  }

  public ManualNotificationLanguages snapshot() {
    return validated(originalLanguage, translations);
  }
}
