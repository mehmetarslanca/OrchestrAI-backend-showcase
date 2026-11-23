package com.arslanca.orchestrai.business.delivery.policy;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DefaultReservedPathPolicy implements ReservedPathPolicy {
    @Override
    public boolean isReserved(final String path) {
        final String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.equals(".cursorrules")
            || lowerPath.startsWith(".agents/")
            || lowerPath.equals("docs/architect.md")
            || lowerPath.startsWith("docs/phases/")
            || lowerPath.startsWith("docs/agents/");
    }
}
