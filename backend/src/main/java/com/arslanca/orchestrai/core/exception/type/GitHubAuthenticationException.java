package com.arslanca.orchestrai.core.exception.type;

/**
 * GitHub API çağrıları sırasında oluşan hataları temsil eder.
 * Token exchange veya user info çekme başarısız olduğunda fırlatılır.
 */
public class GitHubAuthenticationException extends RuntimeException {

    public GitHubAuthenticationException(final String message) {
        super(message);
    }

    public GitHubAuthenticationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
