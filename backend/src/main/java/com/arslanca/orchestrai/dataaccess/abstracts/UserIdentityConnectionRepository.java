package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.UserIdentityConnectionEntity;
import com.arslanca.orchestrai.entity.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityConnectionRepository extends JpaRepository<UserIdentityConnectionEntity, UUID> {

    Optional<UserIdentityConnectionEntity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    List<UserIdentityConnectionEntity> findAllByUserId(UUID userId);
}
