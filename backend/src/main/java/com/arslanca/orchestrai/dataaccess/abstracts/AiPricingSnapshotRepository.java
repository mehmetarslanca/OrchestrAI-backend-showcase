package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.AiPricingSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiPricingSnapshotRepository extends JpaRepository<AiPricingSnapshotEntity, UUID> {
}
