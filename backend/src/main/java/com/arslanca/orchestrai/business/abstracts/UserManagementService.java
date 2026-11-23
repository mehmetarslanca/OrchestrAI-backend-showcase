package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.auth.IdentityProfile;
import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import com.arslanca.orchestrai.entity.concretes.UserEntity;

import java.util.UUID;

/**
 * Kullanıcı bilgisi yönetimi için servis sözleşmesi.
 *
 * <p>
 * Mevcut kullanıcının profil bilgilerini JWT'den gelen ID ile çeker.
 * İş mantığı detayları {@code UserManager} implementasyonunda.
 * </p>
 */
public interface UserManagementService {

    /**
     * JWT'den çıkarılan kullanıcı ID'si ile kullanıcı profilini döner.
     *
     * @param userId JWT'den gelen kullanıcı UUID'si
     * @return kullanıcı profil DTO'su
     * @throws IllegalStateException kullanıcı bulunamazsa
     */
    UserResponse getCurrentUser(UUID userId);

    /**
     * Identity provider bilgileriyle kullanici arar veya yeni yaratir.
     * Transactional olarak çalışır ve Entity döner (Internal kullanım içindir).
     *
     * @param profile provider'dan dogrulanmis identity profili
     * @return UserEntity kaydı
     */
    UserEntity findOrCreateUser(IdentityProfile profile);

    UserResponse updateUserProfile(UUID userId, com.arslanca.orchestrai.business.dtos.user.UserUpdateRequest request);

    void deleteAccount(UUID userId);
}
