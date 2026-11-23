package com.arslanca.orchestrai.business.concretes.delivery;

import com.arslanca.orchestrai.business.abstracts.delivery.IdeDeliveryStrategy;
import com.arslanca.orchestrai.entity.enums.IdeTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory class that selects the appropriate IdeDeliveryStrategy
 * based on the user's selected IDE target.
 */
@Component
public class IdeDeliveryStrategyFactory {

    private final Map<String, IdeDeliveryStrategy> strategyMap;

    public IdeDeliveryStrategyFactory(List<IdeDeliveryStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        IdeDeliveryStrategy::supportedIde,
                        Function.identity(),
                        (existing, replacement) -> existing));
    }

    /**
     * Returns the matching strategy for the given IdeTarget.
     * Maps unknown or unsupported targets to the Generic strategy.
     */
    public IdeDeliveryStrategy getStrategy(final IdeTarget ideTarget) {
        final String targetKey = ideTarget.getStrategyKey();

        final IdeDeliveryStrategy strategy = strategyMap.get(targetKey);
        if (strategy != null) {
            return strategy;
        }

        final IdeDeliveryStrategy fallback = strategyMap.get("generic");
        if (fallback == null) {
            throw new IllegalStateException(
                    "No delivery strategy found for IDE target '" + targetKey
                            + "' and no generic fallback is registered");
        }
        return fallback;
    }
}
