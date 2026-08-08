package com.fundcompass.program.service;

import com.fundcompass.program.repository.ProgramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class ProgramSyncServiceTest {

    @Autowired
    ProgramSyncService programSyncService;

    @Autowired
    ProgramRepository programRepository;

    @Test
    @DisplayName("두 번 실행해도 데이터가 중복되지 않는다")
    void 멱등성_검증() {
        programRepository.deleteAll();

        var first = programSyncService.sync(10);
        long countAfterFirst = programRepository.count();

        var second = programSyncService.sync(10);
        long countAfterSecond = programRepository.count();

        assertThat(first.inserted()).isEqualTo(10);

        assertThat(countAfterFirst).isEqualTo(countAfterSecond);
        assertThat(second.inserted()).isZero();
        assertThat(second.failed()).isZero();
    }
}
