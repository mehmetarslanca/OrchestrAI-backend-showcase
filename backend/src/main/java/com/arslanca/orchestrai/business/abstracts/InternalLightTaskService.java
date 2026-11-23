package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import java.util.UUID;

public interface InternalLightTaskService {

    /**
     * Ilk mesaj geldikten sonra eger proje auto-named ise yeni bir isim uretir ve DB'yi gunceller.
     * Scalar degerler (ID ve autoNamed flag) doğrudan gecirilir; proxy'den okunmaz.
     */
    default void suggestProjectNameAsync(final UUID projectId, final boolean isAutoNamed, final String firstUserMessage) {
        suggestProjectNameAsync(null, projectId, isAutoNamed, firstUserMessage);
    }

    void suggestProjectNameAsync(UUID userId, UUID projectId, boolean isAutoNamed, String firstUserMessage);

    /**
     * Ilk mesaj geldikten sonra eger chat auto-named ise yeni bir baslik uretir ve DB'yi gunceller.
     */
    default void suggestChatTitleAsync(final ChatEntity chat, final String firstUserMessage) {
        suggestChatTitleAsync(null, chat, firstUserMessage);
    }

    void suggestChatTitleAsync(UUID userId, ChatEntity chat, String firstUserMessage);
}
