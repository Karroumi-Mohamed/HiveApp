package com.hiveapp.platform.client.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.shared.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SubscriptionSnapshotReader {

    private final ObjectMapper objectMapper;

    public Optional<SubscriptionEntitlementSnapshot> read(Object rawSnapshot) {
        if (isEmpty(rawSnapshot)) {
            return Optional.empty();
        }
        if (rawSnapshot instanceof String value) {
            try {
                return Optional.of(objectMapper.readValue(value, SubscriptionEntitlementSnapshot.class));
            } catch (IOException e) {
                throw new InvalidRequestException("Invalid subscription entitlement snapshot JSON.", e);
            }
        }
        try {
            return Optional.of(objectMapper.convertValue(rawSnapshot, SubscriptionEntitlementSnapshot.class));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid subscription entitlement snapshot.", e);
        }
    }

    public String write(SubscriptionEntitlementSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (IOException e) {
            throw new InvalidRequestException("Invalid subscription entitlement snapshot.", e);
        }
    }

    private static boolean isEmpty(Object rawSnapshot) {
        if (rawSnapshot == null) {
            return true;
        }
        if (rawSnapshot instanceof String value) {
            return value.isBlank() || "null".equalsIgnoreCase(value.trim());
        }
        if (rawSnapshot instanceof JsonNode node) {
            return node.isNull()
                    || (node.isTextual() && "null".equalsIgnoreCase(node.asText().trim()));
        }
        return false;
    }
}
