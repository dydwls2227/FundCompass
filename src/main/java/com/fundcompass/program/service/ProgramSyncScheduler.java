package com.fundcompass.program.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "fundcompass.scheduler.program-sync.enabled",
        havingValue = "true"
)
public class ProgramSyncScheduler {
    private final ProgramSyncService programSyncService;

    @Scheduled(cron = "${fundcompass.scheduler.program-sync.cron}", zone = "Asia/Seoul")
    public void syncPrograms() {
        log.info("공고 동기화 배치 시작");
        try{
            ProgramSyncService.SyncResult result = programSyncService.sync(0);
            log.info("공고 동기화 배치 완료 - {}", result);
        }catch (Exception e){
            log.error("공고 동기화 배치 실패", e);
        }
    }
}
