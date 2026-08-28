package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommercialOfferCodeHasher {
  private static final byte[] DOMAIN =
      "hiveapp:commercial-offer-code:v1".getBytes(StandardCharsets.UTF_8);
  private final CommercialOfferCodeProperties properties;

  public String hash(String raw) {
    String normalized = CommercialOffer.normalizeCode(raw);
    if (normalized == null) return null;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(properties.pepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(DOMAIN);
      mac.update((byte) 0);
      return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Offer-code HMAC is unavailable", e);
    }
  }
}
