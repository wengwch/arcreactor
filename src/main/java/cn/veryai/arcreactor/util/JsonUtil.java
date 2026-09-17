package cn.veryai.arcreactor.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared JSON conversion. Null/blank input and JSON null deserialize to null.
 * Invalid JSON or serialization failures throw IllegalArgumentException with the original cause.
 * Target types must be non-null. Numbers retain the existing string serialization format.
 */
public final class JsonUtil {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonUtil() {
    }

    public static String toJson(Object object) {
        if (object == null) return null;
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Cannot serialize object to JSON", exception);
        }
    }

    public static <T> T toObj(String json, Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        return read(json, MAPPER.getTypeFactory().constructType(clazz));
    }

    /** Supports nested generic types, such as Map<String, List<Value>>. */
    public static <T> T toObj(String json, TypeReference<T> type) {
        Objects.requireNonNull(type, "type");
        return read(json, MAPPER.getTypeFactory().constructType(type));
    }

    public static <T> List<T> toList(String json, Class<T> elementClazz) {
        Objects.requireNonNull(elementClazz, "elementClazz");
        return read(json, MAPPER.getTypeFactory().constructCollectionType(List.class, elementClazz));
    }

    public static <K, V> Map<K, V> toMap(String json, Class<K> keyClazz, Class<V> valueClazz) {
        Objects.requireNonNull(keyClazz, "keyClazz");
        Objects.requireNonNull(valueClazz, "valueClazz");
        return read(json, MAPPER.getTypeFactory().constructMapType(Map.class, keyClazz, valueClazz));
    }

    private static <T> T read(String json, JavaType type) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Cannot deserialize JSON to " + type.toCanonical(), exception);
        }
    }
}
