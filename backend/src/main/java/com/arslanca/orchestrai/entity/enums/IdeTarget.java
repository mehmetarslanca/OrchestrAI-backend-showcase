package com.arslanca.orchestrai.entity.enums;

public enum IdeTarget {
    CURSOR("cursor"),
    ANTIGRAVITY("antigravity"),
    WINDSURF("antigravity"), // Windsurf natively uses antigravity strategy
    GENERIC("generic");

    private final String strategyKey;

    IdeTarget(String strategyKey) {
        this.strategyKey = strategyKey;
    }

    public String getStrategyKey() {
        return strategyKey;
    }
}
