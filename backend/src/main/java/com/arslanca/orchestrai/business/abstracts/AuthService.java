package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.auth.AuthResponse;

public interface AuthService {

    /**
     * GitHub OAuth authorization code'u işler:
     * 1. Code → access_token
     * 2. access_token → GitHub user info
     * 3. User yoksa oluştur + kredi ata
     * 4. JWT üret ve döndür
     */
    AuthResponse authenticateWithGitHub(String authorizationCode);

    AuthResponse authenticateWithGoogle(String authorizationCode);

    AuthResponse refreshSession(String refreshToken);

    void logout(java.util.UUID sessionId);
}
