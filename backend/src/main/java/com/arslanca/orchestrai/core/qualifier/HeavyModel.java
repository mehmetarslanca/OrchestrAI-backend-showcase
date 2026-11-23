package com.arslanca.orchestrai.core.qualifier;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom qualifier annotation for the heavy/deep-thinking LLM model bean.
 * Used for Stage 2 (Chief Architect) in the dual-LLM pipeline.
 *
 * <p>
 * This annotation solves the Lombok {@code @RequiredArgsConstructor} +
 * {@code @Qualifier} incompatibility — Lombok copies custom annotations
 * (but not {@code @Qualifier}) to constructor parameters.
 * </p>
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface HeavyModel {
}
