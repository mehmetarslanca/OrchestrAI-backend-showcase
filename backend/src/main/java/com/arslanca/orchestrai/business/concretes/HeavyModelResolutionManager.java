package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelUserPreferenceService;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.DiscoveredHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyModelCapabilityResolver;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.HeavyModelKeyNormalizer;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadata;
import com.arslanca.orchestrai.core.ai.provider.ProviderRuntimeMetadataResolver;
import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HeavyModelResolutionManager implements HeavyModelResolutionService {

  private final HeavyModelCatalogManager heavyModelCatalogManager;
  private final HeavyModelUserPreferenceService heavyModelUserPreferenceService;
  private final AiModelProperties aiModelProperties;
  private final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver;
  private final HeavyModelCapabilityResolver heavyModelCapabilityResolver;
  private final List<HeavyProviderAdapter> heavyProviderAdapters;

  public HeavyModelResolutionManager(
      final HeavyModelCatalogManager heavyModelCatalogManager,
      final HeavyModelUserPreferenceService heavyModelUserPreferenceService,
      final AiModelProperties aiModelProperties,
      final ProviderRuntimeMetadataResolver providerRuntimeMetadataResolver,
      final HeavyModelCapabilityResolver heavyModelCapabilityResolver,
      final List<HeavyProviderAdapter> heavyProviderAdapters) {
    this.heavyModelCatalogManager = heavyModelCatalogManager;
    this.heavyModelUserPreferenceService = heavyModelUserPreferenceService;
    this.aiModelProperties = aiModelProperties;
    this.providerRuntimeMetadataResolver = providerRuntimeMetadataResolver;
    this.heavyModelCapabilityResolver = heavyModelCapabilityResolver;
    this.heavyProviderAdapters = List.copyOf(heavyProviderAdapters);
  }

  @Override
  public ResolvedHeavyModel resolve(final String requestedHeavyModelKey) {
    return resolve(null, requestedHeavyModelKey, null);
  }

  @Override
  public ResolvedHeavyModel resolve(
      final UUID userId,
      final String requestedHeavyModelKey,
      final CredentialSource requestedAuthSource) {
    final List<DiscoveredHeavyModel> availableModels = heavyModelCatalogManager.getAvailableModels(userId);
    final List<DiscoveredHeavyModel> availablePlatformModels = heavyModelCatalogManager
        .getAvailablePlatformModels();

    final String normalizedRequestedKey = normalizeRequestedKey(requestedHeavyModelKey);
    if (normalizedRequestedKey != null) {
      return resolveExplicitRequest(userId, normalizedRequestedKey, requestedAuthSource, availableModels);
    }

    final Optional<HeavyModelPreference> preference = userId == null
        ? Optional.empty()
        : heavyModelUserPreferenceService.findPreference(userId);
    if (preference.isPresent()) {
      final HeavyModelPreference heavyModelPreference = preference.get();
      return findMatchingModel(
          availableModels,
          heavyModelPreference.modelKey(),
          heavyModelPreference.authSource(),
          heavyModelPreference.selectedConnectionId())
          .map(this::toResolvedModel)
          .orElseThrow(() -> HeavyModelResolutionException.badRequest(
              "Kayitli default heavy model artik kullanilamiyor. Lutfen AI ayarlarinizi guncelleyin."));
    }

    if (availablePlatformModels.isEmpty()) {
      throw HeavyModelResolutionException.serviceUnavailable(
          "Platform tarafinda erisilebilir heavy model bulunamadi.");
    }

    return selectPlatformDefault(availablePlatformModels);
  }

  @Override
  public HeavyProviderAdapter adapterFor(final ResolvedHeavyModel resolvedHeavyModel) {
    return heavyProviderAdapters.stream()
        .filter(adapter -> adapter.supports(resolvedHeavyModel))
        .findFirst()
        .orElseThrow(() -> HeavyModelResolutionException.serviceUnavailable(
            "Secilen heavy model provider'i sistemde aktif degil."));
  }

  private ResolvedHeavyModel resolveExplicitRequest(
      final UUID userId,
      final String normalizedRequestedKey,
      final CredentialSource requestedAuthSource,
      final List<DiscoveredHeavyModel> availableModels) {
    if (requestedAuthSource != null) {
      final List<DiscoveredHeavyModel> matches = matchingModels(availableModels, normalizedRequestedKey, requestedAuthSource);
      if (matches.isEmpty()) {
        throw HeavyModelResolutionException.badRequest(
            "Istenen heavy model belirtilen auth source ile kullanilamiyor.");
      }

      final Optional<HeavyModelPreference> userPreference = userId == null
          ? Optional.empty()
          : heavyModelUserPreferenceService.findPreference(userId);
      return selectPreferredMatch(matches, userPreference)
          .map(this::toResolvedModel)
          .orElseGet(() -> toResolvedModel(matches.get(0)));
    }

    final List<DiscoveredHeavyModel> matches = matchingModels(availableModels, normalizedRequestedKey, null);
    if (matches.isEmpty()) {
      throw HeavyModelResolutionException.badRequest(
          "Istenen heavy model kullanilamiyor veya onayli katalogda yer almiyor.");
    }
    if (matches.size() == 1) {
      return toResolvedModel(matches.get(0));
    }

    final Optional<HeavyModelPreference> userPreference = userId == null
        ? Optional.empty()
        : heavyModelUserPreferenceService.findPreference(userId);
    final Optional<DiscoveredHeavyModel> preferredMatch = selectPreferredMatch(matches, userPreference);
    if (preferredMatch.isPresent()) {
      return toResolvedModel(preferredMatch.get());
    }

    return toResolvedModel(prioritizeUserRoute(matches));
  }

  private ResolvedHeavyModel selectPlatformDefault(final List<DiscoveredHeavyModel> availableModels) {
      final String preferredPlatformKey = normalizeRequestedKey(aiModelProperties.heavyModel());
    if (preferredPlatformKey != null) {
      final var preferredModel = findMatchingModel(availableModels, preferredPlatformKey,
          CredentialSource.PLATFORM, null);
      if (preferredModel.isPresent()) {
        return toResolvedModel(preferredModel.get());
      }
    }

    return toResolvedModel(availableModels.get(0));
  }

  private java.util.Optional<DiscoveredHeavyModel> findMatchingModel(
      final List<DiscoveredHeavyModel> availableModels,
      final String normalizedModelKey,
      final CredentialSource authSource,
      final UUID selectedConnectionId) {
    final List<DiscoveredHeavyModel> matches = matchingModels(availableModels, normalizedModelKey, authSource);
    if (selectedConnectionId != null) {
      final Optional<DiscoveredHeavyModel> selectedConnectionMatch = matches.stream()
          .filter(model -> selectedConnectionId.equals(model.selectedConnectionId()))
          .findFirst();
      if (selectedConnectionMatch.isPresent()) {
        return selectedConnectionMatch;
      }
    }
    return matches.stream().findFirst();
  }

  private List<DiscoveredHeavyModel> matchingModels(
      final List<DiscoveredHeavyModel> availableModels,
      final String normalizedModelKey,
      final CredentialSource authSource) {
    return availableModels.stream()
        .filter(model -> model.modelKey().equals(normalizedModelKey))
        .filter(model -> authSource == null || model.source() == authSource)
        .toList();
  }

  private Optional<DiscoveredHeavyModel> selectPreferredMatch(
      final List<DiscoveredHeavyModel> matches,
      final Optional<HeavyModelPreference> userPreference) {
    if (userPreference.isEmpty()) {
      return Optional.empty();
    }

    final HeavyModelPreference preference = userPreference.get();
    if (!matches.isEmpty() && !matches.get(0).modelKey().equals(preference.modelKey())) {
      return Optional.empty();
    }

    return findMatchingModel(matches, preference.modelKey(), preference.authSource(), preference.selectedConnectionId());
  }

  private DiscoveredHeavyModel prioritizeUserRoute(final List<DiscoveredHeavyModel> matches) {
    return matches.stream()
        .sorted((left, right) -> Integer.compare(sourcePriority(left.source()), sourcePriority(right.source())))
        .findFirst()
        .orElse(matches.get(0));
  }

  private int sourcePriority(final CredentialSource source) {
    return switch (source) {
      case BYOK -> 0;
      case USER_LINKED -> 1;
      case PLATFORM -> 2;
    };
  }

  private ResolvedHeavyModel toResolvedModel(final DiscoveredHeavyModel model) {
    final ProviderRuntimeMetadata runtimeMetadata = providerRuntimeMetadataResolver.resolveForModel(
        model.provider(),
        model.family(),
        model.concreteModelId());
    return new ResolvedHeavyModel(
        model.modelKey(),
        model.provider(),
        model.family(),
        model.source(),
        model.credentialOwner(),
        model.concreteModelId(),
        model.displayName(),
        runtimeMetadata.testOnly(),
        runtimeMetadata.emulated(),
        runtimeMetadata.gateway(),
        runtimeMetadata.runtimeProvider(),
        runtimeMetadata.runtimeTarget(),
        runtimeMetadata.badges(),
        heavyModelCapabilityResolver.resolveCapability(model.provider(), model.family(), runtimeMetadata),
        model.selectedConnectionId(),
        model.executionClass(),
        model.connectionType());
  }

  private String normalizeRequestedKey(final String requestedHeavyModelKey) {
    try {
      return HeavyModelKeyNormalizer.normalize(requestedHeavyModelKey);
    } catch (final IllegalArgumentException ex) {
      throw HeavyModelResolutionException.badRequest(ex.getMessage());
    }
  }
}
