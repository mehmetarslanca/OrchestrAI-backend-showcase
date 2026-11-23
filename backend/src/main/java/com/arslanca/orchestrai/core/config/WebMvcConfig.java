package com.arslanca.orchestrai.core.config;

import com.arslanca.orchestrai.core.idempotency.IdempotencyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(IdempotencyInterceptor idempotencyInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.idempotencyInterceptor = idempotencyInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns(
                        "/api/v*/projects/generate",
                        "/api/v*/projects/generate/",
                        "/api/v*/project-generation/previews/*/accept",
                        "/api/v*/project-generation/previews/*/accept/",
                        "/api/v*/project-generation/start",
                        "/api/v*/project-generation/start/");

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**");
    }
}
