package com.fundcompass.program.service;

import com.fundcompass.program.repository.ProgramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ProgramFullSyncManualTest {
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramSyncService programSyncService;

    @Test
    @DisplayName("전체 공고를 수집한다.")
    void 전체_수집() {
        var result = programSyncService.sync(0);

        System.out.println("=".repeat(60));
        System.out.println("수집 결과: " + result);
        System.out.println("DB 총 건수: " + programRepository.count());
        System.out.println("=".repeat(60));
    }
}
