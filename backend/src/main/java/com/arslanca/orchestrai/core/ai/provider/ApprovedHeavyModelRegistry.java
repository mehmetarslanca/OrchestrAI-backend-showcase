package com.arslanca.orchestrai.core.ai.provider;

import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ApprovedHeavyModelRegistry {

    private static final Map<String, Integer> PROVIDER_ORDER = Map.of(
            "google", 0,
            "openai", 1,
            "github", 2,
            "anthropic", 3,
            "ollama", 4,
            "openai-compatible", 5,
            "moonshot", 6,
            "zai", 7,
            "xai", 8);

    private static final Map<String, List<ApprovedFamily>> APPROVED_FAMILIES = Map.of(
            "openai", List.of(
                    family("gpt-5.4", "GPT-5.4"),
                    family("gpt-5.4-mini", "GPT-5.4 Mini"),
                    family("gpt-5.4-nano", "GPT-5.4 Nano"),
                    family("gpt-4.1", "GPT-4.1"),
                    family("gpt-4.1-mini", "GPT-4.1 Mini"),
                    family("gpt-4o", "GPT-4o"),
                    family("gpt-4o-mini", "GPT-4o Mini"),
                    family("o3", "o3"),
                    family("o4-mini", "o4-mini")),
            "google", List.of(
                    family("gemini-3.1-pro-preview", "Gemini 3.1 Pro"),
                    family("gemini-3-pro-preview", "Gemini 3 Pro"),
                    family("gemini-3-flash-preview", "Gemini 3 Flash"),
                    family("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash-Lite"),
                    family("gemini-2.5-pro", "Gemini 2.5 Pro"),
                    family("gemini-2.5-flash", "Gemini 2.5 Flash"),
                    family("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
                    family("gemini-2.0-flash", "Gemini 2.0 Flash"),
                    family("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite")),
            "anthropic", List.of(
                    family("claude-opus-4-6", "Claude Opus 4.6"),
                    family("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                    family("claude-haiku-4-5", "Claude Haiku 4.5"),
                    family("claude-sonnet-4-5", "Claude Sonnet 4.5"),
                    family("claude-opus-4-5", "Claude Opus 4.5"),
                    family("claude-opus-4-1", "Claude Opus 4.1"),
                    family("claude-sonnet-4-0", "Claude Sonnet 4"),
                    family("claude-opus-4-0", "Claude Opus 4"),
                    family("claude-3-haiku-20240307", "Claude 3 Haiku")),
            "ollama", List.of(
                    family("gemma4-e4b", "Gemma 4 E4B"),
                    family("qwen2.5-coder-14b", "Qwen2.5 Coder 14B"),
                    family("qwen2.5-coder-7b", "Qwen2.5 Coder 7B"),
                    family("llama-3.1-8b-instruct", "Llama 3.1 8B Instruct"),
                    family("deepseek-r1-distill-qwen-14b", "DeepSeek R1 Distill Qwen 14B")),
            "openai-compatible", List.of(
                    family("gemma4-e4b", "Gemma 4 E4B"),
                    family("qwen2.5-coder-14b", "Qwen2.5 Coder 14B"),
                    family("qwen2.5-coder-7b", "Qwen2.5 Coder 7B"),
                    family("llama-3.1-8b-instruct", "Llama 3.1 8B Instruct"),
                    family("deepseek-r1-distill-qwen-14b", "DeepSeek R1 Distill Qwen 14B")),
            "moonshot", List.of(family("kimi-k2.5", "Kimi K2.5")),
            "zai", List.of(family("glm-5", "GLM-5")),
            "xai", List.of(family("grok-4.20", "Grok 4.20")));

    private ApprovedHeavyModelRegistry() {
    }

    public static Optional<ApprovedFamily> normalize(final String providerKey, final String rawModelId) {
        final String normalizedModelId = normalizeKey(rawModelId);
        return familiesFor(providerKey).stream()
                .sorted(Comparator.comparingInt((ApprovedFamily family) -> family.family().length()).reversed())
                .filter(family -> matches(normalizedModelId, family.family()))
                .findFirst();
    }

    public static int providerOrder(final String providerKey) {
        return PROVIDER_ORDER.getOrDefault(providerKey, Integer.MAX_VALUE);
    }

    public static int familyOrder(final String providerKey, final String family) {
        final List<ApprovedFamily> families = familiesFor(providerKey);
        for (int index = 0; index < families.size(); index++) {
            if (families.get(index).family().equals(family)) {
                return index;
            }
        }
        return fallbackFamilyOrder(family);
    }

    public static int sourceOrder(final CredentialSource source) {
        return source == CredentialSource.PLATFORM ? 0 : 1;
    }

    public static List<ApprovedFamily> familiesFor(final String providerKey) {
        return APPROVED_FAMILIES.getOrDefault(providerKey, List.of());
    }

    public static String normalizeKey(final String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim().toLowerCase();
        final int lastSlashIndex = normalized.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < normalized.length() - 1) {
            normalized = normalized.substring(lastSlashIndex + 1);
        }
        return normalized.replace('_', '-').replace(':', '-');
    }

    private static boolean matches(final String normalizedModelId, final String canonicalFamily) {
        return normalizedModelId.equals(canonicalFamily)
                || normalizedModelId.startsWith(canonicalFamily + "-")
                || normalizedModelId.startsWith(canonicalFamily + ".")
                || normalizedModelId.startsWith(canonicalFamily + ":");
    }

    private static int fallbackFamilyOrder(final String family) {
        final String normalized = family == null ? "" : family.toLowerCase(Locale.ROOT);
        if (normalized.contains("opus") || normalized.endsWith("-pro") || normalized.contains("-pro-")) {
            return 100;
        }
        if (normalized.contains("sonnet") || normalized.endsWith("-flash") || normalized.contains("-flash-")) {
            return 200;
        }
        if (normalized.contains("haiku") || normalized.contains("lite")) {
            return 300;
        }
        return Integer.MAX_VALUE;
    }

    private static ApprovedFamily family(final String family, final String displayName) {
        return new ApprovedFamily(family, displayName);
    }

    public record ApprovedFamily(String family, String displayName) {
    }
}
