package com.arslanca.orchestrai.core.ai;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ProviderFailureClassifier {

    public enum FailureType {
        RATE_LIMIT,
        QUOTA_EXCEEDED,
        INVALID_CREDENTIALS,
        PROVIDER_UNAVAILABLE,
        NETWORK_FAILURE,
        UNKNOWN_PROVIDER_FAILURE
    }

    public static FailureType classify(Throwable error) {
        final String messageCorpus = buildMessageCorpus(error);

        if (messageCorpus.contains("429")) {
            if (messageCorpus.contains("quota") || messageCorpus.contains("insufficient_quota")) {
                return FailureType.QUOTA_EXCEEDED;
            }
            return FailureType.RATE_LIMIT;
        } else if (messageCorpus.contains("401") || messageCorpus.contains("invalid_api_key")) {
            return FailureType.INVALID_CREDENTIALS;
        } else if (messageCorpus.contains("500") || messageCorpus.contains("502") || messageCorpus.contains("503")) {
            return FailureType.PROVIDER_UNAVAILABLE;
        } else if (messageCorpus.contains("timeout")
                || messageCorpus.contains("request cancelled")
                || messageCorpus.contains("connection")
                || messageCorpus.contains("broken pipe")) {
            return FailureType.NETWORK_FAILURE;
        }

        org.slf4j.LoggerFactory.getLogger(ProviderFailureClassifier.class).warn("Unknown provider failure encountered", error);
        return FailureType.UNKNOWN_PROVIDER_FAILURE;
    }

    private static String buildMessageCorpus(final Throwable error) {
        final StringBuilder corpus = new StringBuilder();
        final Set<Throwable> visited = new HashSet<>();
        Throwable current = error;

        while (current != null && visited.add(current)) {
            if (current.getMessage() != null) {
                if (corpus.length() > 0) {
                    corpus.append(' ');
                }
                corpus.append(current.getMessage().toLowerCase(Locale.ROOT));
            }
            current = current.getCause();
        }

        return corpus.toString();
    }

    public static String getUserFriendlyMessage(FailureType type) {
        switch (type) {
            case RATE_LIMIT:
                return "Sistem geçici olarak yoğun. Krediniz iade edildi, lütfen biraz sonra tekrar deneyin.";
            case QUOTA_EXCEEDED:
                return "Yapay zeka sağlayıcı kotası doldu. Krediniz iade edildi.";
            case INVALID_CREDENTIALS:
                return "Yapay zeka sağlayıcı kimlik doğrulama hatası. Krediniz iade edildi.";
            case PROVIDER_UNAVAILABLE:
                return "Yapay zeka sağlayıcısı şu anda yanıt veremiyor. Krediniz iade edildi.";
            case NETWORK_FAILURE:
                return "Bağlantı hatası oluştu. Krediniz iade edildi.";
            default:
                return "Beklenmeyen bir hata oluştu. Krediniz iade edildi.";
        }
    }
}
