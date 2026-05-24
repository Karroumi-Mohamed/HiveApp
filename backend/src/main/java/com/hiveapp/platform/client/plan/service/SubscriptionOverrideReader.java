package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionOverrideReader {

    private final ObjectMapper objectMapper;

    public SubscriptionOverrides read(Object rawOverrides) {
        if (isEmpty(rawOverrides)) {
            return SubscriptionOverrides.empty();
        }
        if (rawOverrides instanceof String value) {
            try {
                return objectMapper.readValue(value, SubscriptionOverrides.class);
            } catch (IOException e) {
                throw new InvalidRequestException("Invalid subscription override JSON.", e);
            }
        }
        try {
            return objectMapper.convertValue(rawOverrides, SubscriptionOverrides.class);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid subscription overrides.", e);
        }
    }

    public String write(SubscriptionOverrides overrides) {
        try {
            return objectMapper.writeValueAsString(overrides != null ? overrides : SubscriptionOverrides.empty());
        } catch (IOException e) {
            throw new InvalidRequestException("Invalid subscription overrides.", e);
        }
    }

    private static boolean isEmpty(Object rawOverrides) {
        if (rawOverrides == null) {
            return true;
        }
        if (rawOverrides instanceof String value) {
            return value.isBlank() || "null".equalsIgnoreCase(value.trim());
        }
        if (rawOverrides instanceof JsonNode node) {
            return node.isNull()
                    || (node.isTextual() && "null".equalsIgnoreCase(node.asText().trim()));
        }
        return false;
    }
}
