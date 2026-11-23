package com.arslanca.orchestrai.core.job;

import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.business.concretes.DistributedSemaphoreManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SemaphoreReconciliationJob {

    private final ProjectRepository projectRepository;
    private final StringRedisTemplate redisTemplate;

    public SemaphoreReconciliationJob(ProjectRepository projectRepository, StringRedisTemplate redisTemplate) {
        this.projectRepository = projectRepository;
        this.redisTemplate = redisTemplate;
    }


    private static final String SEMAPHORE_KEY = DistributedSemaphoreManager.semaphoreKey("active_count");

    @Scheduled(fixedRate = 300000)
    public void reconcileSemaphore() {
        try {
            List<ProjectEntity> activeProjects = projectRepository.findByStatus(ProjectStatus.PROCESSING);
            Set<String> activeProjectIds = activeProjects.stream()
                .map(p -> p.getId().toString())
                .collect(Collectors.toSet());
                
            Set<String> redisMembers = redisTemplate.opsForSet().members(SEMAPHORE_KEY);
            
            if (redisMembers != null) {
                for (String member : redisMembers) {
                    if (!activeProjectIds.contains(member)) {
                        log.warn("Reconciliation detected stale lease token '{}' in Redis. Removing.", member);
                        redisTemplate.opsForSet().remove(SEMAPHORE_KEY, member);
                    }
                }
            }

            for (String activeProjectId : activeProjectIds) {
                if (redisMembers == null || !redisMembers.contains(activeProjectId)) {
                    log.warn("Reconciliation detected missing lease token '{}' in Redis. Re-adding.", activeProjectId);
                    redisTemplate.opsForSet().add(SEMAPHORE_KEY, activeProjectId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to reconcile semaphore: {}", e.getMessage());
        }
    }
}
