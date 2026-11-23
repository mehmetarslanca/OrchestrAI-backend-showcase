package com.arslanca.orchestrai.business.planning.governance;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlanningGovernanceSnapshotCodec {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public PlanningGovernanceSnapshotCodec(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toPayload(final PlanningGovernanceSnapshot snapshot) {
        if (snapshot == null) {
            return Map.of();
        }
        return objectMapper.convertValue(snapshot, MAP_TYPE);
    }

    public PlanningGovernanceSnapshot readSnapshot(final Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new PlanningGovernanceSnapshot(null, null, null, null, null, null, null, null, null, null);
        }
        return objectMapper.convertValue(payload, PlanningGovernanceSnapshot.class);
    }
}
