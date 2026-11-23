package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.PlatformCredentialRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HeavyModelCredentialResolver {

    private final PlatformCredentialRegistry platformCredentialRegistry;
    private final ProviderCredentialManager providerCredentialManager;

    public HeavyModelCredentialResolver(
            final PlatformCredentialRegistry platformCredentialRegistry,
            final ProviderCredentialManager providerCredentialManager) {
        this.platformCredentialRegistry = platformCredentialRegistry;
        this.providerCredentialManager = providerCredentialManager;
    }

    public ProviderCredential resolve(final ResolvedHeavyModel resolvedHeavyModel) {
        if (resolvedHeavyModel.source() == CredentialSource.PLATFORM) {
            return platformCredentialRegistry.findPlatformCredential(resolvedHeavyModel.provider())
                    .orElseThrow(() -> HeavyModelResolutionException.serviceUnavailable(
                            "Secilen platform provider credential'i sistemde aktif degil."));
        }

        if (resolvedHeavyModel.source() == CredentialSource.USER_LINKED) {
            if (resolvedHeavyModel.selectedConnectionId() == null) {
                throw HeavyModelResolutionException.badRequest(
                        "Secilen user-linked AI connection icin selectedConnectionId zorunludur.");
            }
        }

        try {
            final UUID ownerId = UUID.fromString(resolvedHeavyModel.credentialOwner());
            final java.util.Optional<ProviderCredential> providerCredential = resolvedHeavyModel.selectedConnectionId() == null
                    ? providerCredentialManager.findValidCredential(ownerId, resolvedHeavyModel.provider())
                    : providerCredentialManager.findValidCredential(ownerId, resolvedHeavyModel.selectedConnectionId());
            return providerCredential
                    .orElseThrow(() -> HeavyModelResolutionException.badRequest(
                            resolvedHeavyModel.source() == CredentialSource.USER_LINKED
                                    ? "Secilen user-linked AI connection artik gecerli degil veya yeniden yetkilendirme gerektiriyor."
                                    : "Secilen BYOK credential'i artik gecerli degil veya kaldirildi."));
        } catch (final IllegalArgumentException ex) {
            throw HeavyModelResolutionException.serviceUnavailable("Secilen heavy model credential owner bilgisi gecersiz.");
        }
    }
}
