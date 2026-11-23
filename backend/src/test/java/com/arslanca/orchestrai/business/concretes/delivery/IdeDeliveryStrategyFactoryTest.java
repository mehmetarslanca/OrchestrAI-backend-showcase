package com.arslanca.orchestrai.business.concretes.delivery;

import com.arslanca.orchestrai.business.abstracts.delivery.IdeDeliveryStrategy;
import com.arslanca.orchestrai.business.concretes.delivery.strategies.AntigravityDeliveryStrategy;
import com.arslanca.orchestrai.business.concretes.delivery.strategies.CursorDeliveryStrategy;
import com.arslanca.orchestrai.business.concretes.delivery.strategies.GenericDeliveryStrategy;
import com.arslanca.orchestrai.entity.enums.IdeTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IdeDeliveryStrategyFactoryTest {

    private IdeDeliveryStrategyFactory factory;

    @BeforeEach
    void setUp() {
        List<IdeDeliveryStrategy> strategies = List.of(
                new CursorDeliveryStrategy(),
                new AntigravityDeliveryStrategy(),
                new GenericDeliveryStrategy());
        factory = new IdeDeliveryStrategyFactory(strategies);
    }

    @Test
    void shouldReturnCursorStrategy() {
        IdeDeliveryStrategy strategy = factory.getStrategy(IdeTarget.CURSOR);
        assertInstanceOf(CursorDeliveryStrategy.class, strategy);
    }

    @Test
    void shouldReturnAntigravityStrategy() {
        IdeDeliveryStrategy strategy = factory.getStrategy(IdeTarget.ANTIGRAVITY);
        assertInstanceOf(AntigravityDeliveryStrategy.class, strategy);
    }

    @Test
    void shouldReturnAntigravityStrategyForWindsurf() {
        IdeDeliveryStrategy strategy = factory.getStrategy(IdeTarget.WINDSURF);
        assertInstanceOf(AntigravityDeliveryStrategy.class, strategy);
    }

    @Test
    void shouldReturnGenericByDefault() {
        IdeDeliveryStrategy strategy = factory.getStrategy(IdeTarget.GENERIC);
        assertInstanceOf(GenericDeliveryStrategy.class, strategy);
    }
}
