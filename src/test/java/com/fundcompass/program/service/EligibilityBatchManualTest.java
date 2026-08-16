package com.fundcompass.program.service;

import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.ProgramEligibility;
import com.fundcompass.program.repository.ProgramEligibilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EligibilityBatchManualTest {

    @Autowired EligibilityBatchService batchService;
    @Autowired ProgramEligibilityRepository eligibilityRepository;

    @Test
    @DisplayName("남은 대상 확인 (API 호출 없음)")
    void 대상_확인() {
        List<Long> targets = batchService.findTargets();

        System.out.println("=".repeat(72));
        System.out.printf("처리 대기 %,d건%n", targets.size());
        System.out.printf("이미 처리 - 성공 %,d / 실패 %,d%n",
                eligibilityRepository.countByStatus(ExtractionStatus.EXTRACTED),
                eligibilityRepository.countByStatus(ExtractionStatus.FAILED));
        System.out.println("앞 5건 programId: " + targets.stream().limit(5).toList());
        System.out.println("=".repeat(72));
    }

    /**
     * 그날 남은 쿼터에 맞춰 조정한다. 코드 수정 없이 실행할 때 지정한다.
     * {@code ./gradlew test --tests '...추출_쿼터만큼' -Dbatch.size=40}
     */
    private static final int BATCH_SIZE = Integer.getInteger("batch.size", 20);

    @Test
    @DisplayName("남은 쿼터만큼 추출")
    void 추출_쿼터만큼() {
        print(batchService.extractPending(BATCH_SIZE));
    }

    @Test
    @DisplayName("전체 추출")
    void 추출_전체() {
        print(batchService.extractPending(Integer.MAX_VALUE));
    }


    private void print(EligibilityBatchService.BatchResult result) {
        System.out.println("=".repeat(72));
        System.out.println("배치 결과: " + result);
        if (result.quotaExceeded()) {
            System.out.println("⚠️ 쿼터 소진으로 중단 — 다시 실행하면 남은 건부터 이어집니다");
        }
        System.out.println("-".repeat(72));

        List<ProgramEligibility> rows = eligibilityRepository.findAll();

        rows.stream()
                .filter(row -> row.getStatus() == ExtractionStatus.EXTRACTED)
                .sorted(Comparator.comparing(ProgramEligibility::getId).reversed())
                .limit(BATCH_SIZE)
                .forEach(row -> {
                    System.out.printf("%n[%d] programId=%d  프롬프트 %,d자 / %,dms%n",
                            row.getId(), row.getProgramId(),
                            row.getPromptChars(), row.getElapsedMs());
                    System.out.println("  " + row.getExtraction());
                });

        var elapsed = rows.stream()
                .filter(row -> row.getElapsedMs() != null)
                .mapToInt(ProgramEligibility::getElapsedMs)
                .summaryStatistics();
        var chars = rows.stream()
                .filter(row -> row.getPromptChars() != null)
                .mapToInt(ProgramEligibility::getPromptChars)
                .summaryStatistics();

        if (elapsed.getCount() > 0) {
            System.out.printf("%n소요 평균 %,dms (%,d~%,d)   프롬프트 평균 %,d자 (%,d~%,d)   누적 %d건%n",
                    (long) elapsed.getAverage(), elapsed.getMin(), elapsed.getMax(),
                    (long) chars.getAverage(), chars.getMin(), chars.getMax(),
                    elapsed.getCount());
        }
        System.out.println("=".repeat(72));
    }
}