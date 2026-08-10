package com.hiveapp.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hiveapp.shared.domain.BaseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AuditPayloadSanitizer {

    static final String REDACTED = "[REDACTED]";
    private static final int MAX_JSON_LENGTH = 16_000;
    private static final int MAX_COLLECTION_SIZE = 100;
    private static final int MAX_DEPTH = 6;
    private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
            "password", "token", "secret", "credential", "sharecode", "authorization", "cookie", "hash");

    private final ObjectMapper objectMapper;

    String arguments(Method method, Object[] arguments, String[] discoveredNames) {
        Map<String, Object> payload = new LinkedHashMap<>();
        var parameters = method.getParameters();
        for (int index = 0; index < arguments.length; index++) {
            String name = argumentName(parameters, discoveredNames, index);
            payload.put(name, sanitizeNamedValue(name, arguments[index], 0));
        }
        return write(payload);
    }

    String result(Object result) {
        if (result == null) return null;
        return write(sanitizeValue(result, 0));
    }

    String value(Object value) {
        return value == null ? null : write(sanitizeValue(value, 0));
    }

    String extractResourceId(Object result) {
        return extractResourceId(result, 0);
    }

    private String argumentName(java.lang.reflect.Parameter[] parameters, String[] discoveredNames, int index) {
        if (discoveredNames != null && index < discoveredNames.length && discoveredNames[index] != null) {
            return discoveredNames[index];
        }
        if (index < parameters.length && parameters[index].isNamePresent()) {
            return parameters[index].getName();
        }
        return "arg" + index;
    }

    private Object sanitizeNamedValue(String name, Object value, int depth) {
        return isSensitive(name) ? REDACTED : sanitizeValue(value, depth);
    }

    private Object sanitizeValue(Object value, int depth) {
        if (value == null) return null;
        if (depth >= MAX_DEPTH) return "[MAX_DEPTH]";
        if (isSimple(value)) return value;
        if (value instanceof BaseEntity entity) return entitySnapshot(entity, depth + 1);
        if (value instanceof Map<?, ?> map) return sanitizeMap(map, depth + 1);
        if (value instanceof Collection<?> collection) return sanitizeCollection(collection, depth + 1);
        if (value.getClass().isArray()) {
            return sanitizeArray(objectMapper.valueToTree(value), depth + 1);
        }

        try {
            return sanitizeNode(objectMapper.valueToTree(value), depth + 1);
        } catch (IllegalArgumentException exception) {
            return Map.of("type", value.getClass().getSimpleName());
        }
    }

    private Map<String, Object> entitySnapshot(BaseEntity entity, int depth) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", entity.getClass().getSimpleName());
        snapshot.put("id", entity.getId());
        Class<?> type = entity.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || snapshot.containsKey(field.getName())) continue;
                if (isSensitive(field.getName())) {
                    snapshot.put(field.getName(), REDACTED);
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(entity);
                    if (fieldValue == null || isSimple(fieldValue)) {
                        snapshot.put(field.getName(), fieldValue);
                    } else if (fieldValue instanceof Collection<?> collection
                            && collection.stream().allMatch(this::isSimple)) {
                        snapshot.put(field.getName(), sanitizeCollection(collection, depth + 1));
                    }
                } catch (IllegalAccessException ignored) {
                    // An inaccessible nonessential field is omitted from the audit summary.
                }
            }
            type = type.getSuperclass();
        }
        return snapshot;
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> source, int depth) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String name = String.valueOf(key);
            sanitized.put(name, sanitizeNamedValue(name, value, depth));
        });
        return sanitized;
    }

    private List<Object> sanitizeCollection(Collection<?> source, int depth) {
        List<Object> sanitized = new ArrayList<>();
        source.stream().limit(MAX_COLLECTION_SIZE).forEach(value -> sanitized.add(sanitizeValue(value, depth)));
        if (source.size() > MAX_COLLECTION_SIZE) sanitized.add("[TRUNCATED]");
        return sanitized;
    }

    private JsonNode sanitizeNode(JsonNode node, int depth) {
        if (node == null || node.isNull()) return node;
        if (depth >= MAX_DEPTH) return objectMapper.getNodeFactory().textNode("[MAX_DEPTH]");
        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> sanitized.set(
                    entry.getKey(),
                    isSensitive(entry.getKey())
                            ? objectMapper.getNodeFactory().textNode(REDACTED)
                            : sanitizeNode(entry.getValue(), depth + 1)));
            return sanitized;
        }
        if (node.isArray()) return sanitizeArray(node, depth + 1);
        return node;
    }

    private ArrayNode sanitizeArray(JsonNode node, int depth) {
        ArrayNode sanitized = objectMapper.createArrayNode();
        int count = 0;
        for (JsonNode item : node) {
            if (count++ >= MAX_COLLECTION_SIZE) {
                sanitized.add("[TRUNCATED]");
                break;
            }
            sanitized.add(sanitizeNode(item, depth + 1));
        }
        return sanitized;
    }

    private boolean isSimple(Object value) {
        return value instanceof CharSequence
                || value instanceof UUID
                || value instanceof Enum<?>
                || value instanceof Boolean
                || value instanceof Number
                || value instanceof BigDecimal
                || value instanceof BigInteger
                || value instanceof TemporalAccessor;
    }

    private boolean isSensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private String write(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() <= MAX_JSON_LENGTH) return json;
            return objectMapper.writeValueAsString(Map.of(
                    "truncated", true,
                    "originalLength", json.length()));
        } catch (JsonProcessingException exception) {
            return "{\"serializationFailed\":true}";
        }
    }

    private String extractResourceId(Object value, int depth) {
        if (value == null || depth >= 3) return null;
        if (value instanceof BaseEntity entity) {
            return entity.getId() == null ? null : entity.getId().toString();
        }
        if (value instanceof UUID uuid) return uuid.toString();

        for (String accessor : List.of("id", "getId")) {
            String resolved = invokeIdAccessor(value, accessor);
            if (resolved != null) return resolved;
        }
        for (String accessor : List.of(
                "member", "role", "plan", "subscription", "collaboration", "company", "operation", "checkout")) {
            try {
                Method method = value.getClass().getMethod(accessor);
                String resolved = extractResourceId(method.invoke(value), depth + 1);
                if (resolved != null) return resolved;
            } catch (ReflectiveOperationException ignored) {
                // Try the next common result wrapper.
            }
        }
        return null;
    }

    private String invokeIdAccessor(Object value, String accessor) {
        try {
            Method method = value.getClass().getMethod(accessor);
            Object id = method.invoke(value);
            return id instanceof UUID || id instanceof CharSequence ? id.toString() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
