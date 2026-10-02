package com.tubadev.receivables.infrastructure.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Single JSON configuration of the service (snake_case, like the WhatsApp Cloud API). Jackson 3 mappers are
 * immutable and its exceptions unchecked, so the instance is shared as is.
 */
public enum Json {
    INSTANCE;

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    public static JsonMapper mapper() {
        return INSTANCE.mapper;
    }

    public static String writeValueAsString(final Object obj) {
        return INSTANCE.mapper.writeValueAsString(obj);
    }

    public static <T> T readValue(final String json, final Class<T> clazz) {
        return INSTANCE.mapper.readValue(json, clazz);
    }

    public static <T> T readValue(final byte[] json, final Class<T> clazz) {
        return INSTANCE.mapper.readValue(json, clazz);
    }

    public static <T> T readValue(final String json, final TypeReference<T> type) {
        return INSTANCE.mapper.readValue(json, type);
    }
}
