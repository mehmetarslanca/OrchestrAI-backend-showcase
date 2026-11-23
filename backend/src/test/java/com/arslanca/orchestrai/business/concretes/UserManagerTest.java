package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import com.arslanca.orchestrai.dataaccess.abstracts.UserIdentityConnectionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagerTest {

    @Mock
    private com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository projectRepository;
    @Mock
    private com.arslanca.orchestrai.dataaccess.abstracts.CreditTransactionRepository creditTransactionRepository;
    @Mock
    private com.arslanca.orchestrai.dataaccess.abstracts.ConversationProjectRepository conversationProjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserIdentityConnectionRepository userIdentityConnectionRepository;

    @Mock
    private EntityMapper entityMapper;

    private UserManager userManager;

    @BeforeEach
    void setUp() {
        userManager = new UserManager(
                userRepository,
                projectRepository,
                creditTransactionRepository,
                conversationProjectRepository,
                userIdentityConnectionRepository,
                entityMapper);
    }

    @Test
    @DisplayName("getCurrentUser: mevcut kullanıcı için UserResponse döner")
    void shouldReturnUserResponseWhenUserExists() {
        // Arrange
        final UUID userId = UUID.randomUUID();
        final UserEntity entity = mock(UserEntity.class);
        final UserResponse expected = new UserResponse(
                userId, "memet", "memet@example.com",
                "https://avatars.github.com/u/123", "GITHUB", "USER", OffsetDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(entityMapper.toUserResponse(entity)).thenReturn(expected);

        // Act
        final UserResponse result = userManager.getCurrentUser(userId);

        // Assert
        assertThat(result).isEqualTo(expected);
        verify(userRepository).findById(userId);
        verify(entityMapper).toUserResponse(entity);
    }

    @Test
    @DisplayName("getCurrentUser: kullanıcı bulunamazsa IllegalStateException fırlatır")
    void shouldThrowIllegalStateExceptionWhenUserNotFound() {
        // Arrange
        final UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userManager.getCurrentUser(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kullanıcı bulunamadı")
                .hasMessageContaining(userId.toString());

        verifyNoInteractions(entityMapper);
    }

    @Test
    @DisplayName("getCurrentUser: repository sadece bir kez çağrılır")
    void shouldCallRepositoryExactlyOnce() {
        // Arrange
        final UUID userId = UUID.randomUUID();
        final UserEntity entity = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(entityMapper.toUserResponse(entity)).thenReturn(mock(UserResponse.class));

        // Act
        userManager.getCurrentUser(userId);

        // Assert
        verify(userRepository, times(1)).findById(userId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("updateUserProfile: kullanıcı bilgilerini günceller ve kaydeder")
    void shouldUpdateUserProfile() {
        // Arrange
        final UUID userId = UUID.randomUUID();
        final UserEntity userEntity = mock(UserEntity.class);
        when(userEntity.getAvatarUrl()).thenReturn("http://avatar.com/old");

        com.arslanca.orchestrai.business.dtos.user.UserUpdateRequest updateRequest =
                new com.arslanca.orchestrai.business.dtos.user.UserUpdateRequest("newUser", "new@example.com");

        UserResponse expectedResponse = mock(UserResponse.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(entityMapper.toUserResponse(userEntity)).thenReturn(expectedResponse);

        // Act
        UserResponse response = userManager.updateUserProfile(userId, updateRequest);

        // Assert
        verify(userEntity).updateProfile("newUser", "new@example.com", "http://avatar.com/old");
        verify(entityMapper).toUserResponse(userEntity);
        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("deleteAccount: kullanıcının bağlı verilerini ve kendisini siler")
    void shouldDeleteAccountAndCascadingData() {
        // Arrange
        final UUID userId = UUID.randomUUID();
        final UserEntity userEntity = mock(UserEntity.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));

        // Act
        userManager.deleteAccount(userId);

        // Assert
        verify(conversationProjectRepository).deleteAllByUserId(userId);
        verify(projectRepository).deleteAllByUserId(userId);
        verify(creditTransactionRepository).deleteAllByUserId(userId);
        verify(userRepository).delete(userEntity);
    }
}
