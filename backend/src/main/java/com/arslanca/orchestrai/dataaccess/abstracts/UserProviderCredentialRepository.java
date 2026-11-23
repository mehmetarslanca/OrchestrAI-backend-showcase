package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.UserProviderCredentialEntity;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProviderCredentialRepository extends JpaRepository<UserProviderCredentialEntity, UUID> {

    Optional<UserProviderCredentialEntity> findByUserIdAndProviderKey(UUID userId, String providerKey);

    Optional<UserProviderCredentialEntity> findByUserIdAndProviderKeyAndStatus(
            UUID userId,
            String providerKey,
            ProviderCredentialStatus status);

    List<UserProviderCredentialEntity> findAllByUserIdAndStatus(UUID userId, ProviderCredentialStatus status);

    List<UserProviderCredentialEntity> findAllByUserIdAndStatusIn(UUID userId, Collection<ProviderCredentialStatus> statuses);
}
