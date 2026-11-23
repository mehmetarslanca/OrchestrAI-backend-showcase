package com.arslanca.orchestrai.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Jackson configuration for Spring Boot 4.x.
 *
 * <p>
 * Spring Boot 4.x changed how JacksonAutoConfiguration registers beans —
 * it no longer guarantees an {@link ObjectMapper} without explicit definition.
 * This class provides a single, properly configured ObjectMapper bean that
 * is used consistently across:
 * <ul>
 * <li>Spring MVC HTTP message converters (controller responses)</li>
 * <li>Service-layer JSON parsing (e.g., LLM response deserialization)</li>
 * <li>Hibernate JSONB serialization</li>
 * </ul>
 * </p>
 *
 * <p>
 * The {@link JavaTimeModule} is registered to ensure proper serialization of
 * {@code java.time} types (e.g., {@code OffsetDateTime} in entities).
 * </p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
