package com.arslanca.orchestrai.core.config;

import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.arslanca.orchestrai.business.mapper.EntityMapper;

@Configuration
public class MapperConfig {

    @Bean
    @ConditionalOnMissingBean(EntityMapper.class)
    public EntityMapper entityMapper() {
        return Mappers.getMapper(EntityMapper.class);
    }
}
