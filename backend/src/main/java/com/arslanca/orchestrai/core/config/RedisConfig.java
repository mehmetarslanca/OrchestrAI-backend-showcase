package com.arslanca.orchestrai.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public RedisScript<Long> rateLimitScript() {
        return RedisScript.of(new ClassPathResource("scripts/rate_limit.lua"), Long.class);
    }

    @Bean("semaphoreAcquireScript")
    public RedisScript<Long> semaphoreAcquireScript() {
        return RedisScript.of(new ClassPathResource("scripts/semaphore_acquire.lua"), Long.class);
    }
}
