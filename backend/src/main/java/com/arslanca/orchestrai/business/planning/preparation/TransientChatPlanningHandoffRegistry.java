package com.arslanca.orchestrai.business.planning.preparation;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;

@Component
public class TransientChatPlanningHandoffRegistry {

    private final ConcurrentHashMap<UUID, ChatPlanningSnapshot> pendingHandoffs = new ConcurrentHashMap<>();

    public void record(final UUID chatId, final ChatPlanningSnapshot snapshot) {
        if (chatId == null || snapshot == null) {
            return;
        }
        pendingHandoffs.put(chatId, snapshot);
    }

    public Optional<ChatPlanningSnapshot> consume(final UUID chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pendingHandoffs.remove(chatId));
    }

    public void clear(final UUID chatId) {
        if (chatId == null) {
            return;
        }
        pendingHandoffs.remove(chatId);
    }
}
