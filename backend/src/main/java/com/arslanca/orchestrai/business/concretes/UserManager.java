package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.UserManagementService;
import com.arslanca.orchestrai.business.dtos.auth.IdentityProfile;
import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import com.arslanca.orchestrai.business.dtos.user.UserUpdateRequest;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import com.arslanca.orchestrai.dataaccess.abstracts.UserIdentityConnectionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.CreditTransactionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ConversationProjectRepository;
import com.arslanca.orchestrai.entity.concretes.CreditEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.concretes.UserIdentityConnectionEntity;
import com.arslanca.orchestrai.entity.enums.OAuthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class UserManager implements UserManagementService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final ConversationProjectRepository conversationProjectRepository;
    private final UserIdentityConnectionRepository userIdentityConnectionRepository;
    private final EntityMapper entityMapper;

    public UserManager(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            CreditTransactionRepository creditTransactionRepository,
            ConversationProjectRepository conversationProjectRepository,
            UserIdentityConnectionRepository userIdentityConnectionRepository,
            EntityMapper entityMapper) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.conversationProjectRepository = conversationProjectRepository;
        this.userIdentityConnectionRepository = userIdentityConnectionRepository;
        this.entityMapper = entityMapper;
    }


    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(final UUID userId) {
        log.debug("Fetching user profile for userId={}", userId);
        return userRepository.findById(userId)
                .map(entityMapper::toUserResponse)
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı: userId=" + userId));
    }

    @Override
    @Transactional
    public UserEntity findOrCreateUser(final IdentityProfile profile) {
        return userIdentityConnectionRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(connection -> updateExistingUser(profile, connection))
                .orElseGet(() -> findLegacyOrCreateUser(profile));
    }

    @Override
    @Transactional
    public UserResponse updateUserProfile(UUID userId, UserUpdateRequest request) {
        final UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        
        user.updateProfile(
            request.username() != null ? request.username() : user.getUsername(),
            request.email() != null ? request.email() : user.getEmail(),
            user.getAvatarUrl()
        );
        
        return entityMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        final UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        conversationProjectRepository.deleteAllByUserId(userId);
        projectRepository.deleteAllByUserId(userId);
        creditTransactionRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
        
        log.warn("Account deleted permanently: userId={}", userId);
    }

    private UserEntity updateExistingUser(
            final IdentityProfile profile,
            final UserIdentityConnectionEntity connection) {
        final UserEntity existingUser = userRepository.findById(connection.getUserId())
                .orElseThrow(() -> new IllegalStateException("Identity connection points to a missing user: " + connection.getUserId()));
        existingUser.updateProfile(resolveUsername(profile), profile.email(), profile.avatarUrl());
        connection.updateProfile(resolveUsername(profile), profile.email(), profile.avatarUrl());
        userIdentityConnectionRepository.save(connection);
        log.info("Existing user profile updated via identity connection: userId={}, provider={}", existingUser.getId(), profile.provider());
        return existingUser;
    }

    private UserEntity findLegacyOrCreateUser(final IdentityProfile profile) {
        if (profile.provider() == OAuthProvider.GITHUB) {
            final Long githubId = parseGithubId(profile.providerUserId());
            if (githubId != null) {
                final UserEntity legacyUser = userRepository.findByGithubId(githubId).orElse(null);
                if (legacyUser != null) {
                    legacyUser.updateProfile(resolveUsername(profile), profile.email(), profile.avatarUrl());
                    ensureIdentityConnection(legacyUser, profile);
                    log.info("Backfilled identity connection for legacy GitHub user: userId={}", legacyUser.getId());
                    return legacyUser;
                }
            }
        }

        log.info("Creating new user account for provider={}", profile.provider());
        return createNewUser(profile);
    }

    private UserEntity createNewUser(final IdentityProfile profile) {
        final UserEntity newUser = UserEntity.builder()
                .githubId(profile.provider() == OAuthProvider.GITHUB ? parseGithubId(profile.providerUserId()) : null)
                .username(resolveUsername(profile))
                .email(profile.email())
                .avatarUrl(profile.avatarUrl())
                .provider(profile.provider())
                .build();

        final CreditEntity initialCredit = CreditEntity.builder()
                .user(newUser)
                .build();

        newUser.assignInitialCredit(initialCredit);

        final UserEntity savedUser = userRepository.save(newUser);
        ensureIdentityConnection(savedUser, profile);
        return savedUser;
    }

    private void ensureIdentityConnection(final UserEntity user, final IdentityProfile profile) {
        userIdentityConnectionRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .orElseGet(() -> userIdentityConnectionRepository.save(UserIdentityConnectionEntity.create(
                        user.getId(),
                        profile.provider(),
                        profile.providerUserId(),
                        resolveUsername(profile),
                        profile.email(),
                        profile.avatarUrl())));
    }

    private String resolveUsername(final IdentityProfile profile) {
        final String candidate;
        if (profile.username() != null && !profile.username().isBlank()) {
            candidate = profile.username().trim();
        } else if (profile.email() != null && !profile.email().isBlank()) {
            candidate = profile.email().split("@", 2)[0];
        } else {
            candidate = profile.provider().name().toLowerCase() + "-" + profile.providerUserId();
        }
        return candidate.length() <= 100 ? candidate : candidate.substring(0, 100);
    }

    private Long parseGithubId(final String providerUserId) {
        try {
            return providerUserId == null || providerUserId.isBlank() ? null : Long.valueOf(providerUserId);
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
}
