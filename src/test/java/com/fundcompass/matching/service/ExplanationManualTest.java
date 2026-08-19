package com.fundcompass.matching.service;

import com.fundcompass.matching.domain.BusinessProfile;
import com.fundcompass.matching.domain.EligibilityVerdict;
import com.fundcompass.matching.domain.RevenueBand;
import com.fundcompass.matching.dto.ExplanationResponse;
import com.fundcompass.matching.dto.MatchResponse;
import com.fundcompass.matching.dto.ProgramMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

/** 실제 LLM을 호출해 설명 품질을 본다. 판정 유형별로 한 건씩만 — 호출 3회 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ExplanationManualTest {

    @Autowired MatchingService matchingService;
    @Autowired ExplanationService explanationService;

    private static final BusinessProfile 소상공인 = new BusinessProfile(
            LocalDate.of(2024, 3, 1), RevenueBand.UNDER_3, 3,
            "경상북도", "포항시", "음식점업", "소상공인");

    @Test
    @DisplayName("판정 유형별 설명 생성 (LLM 3회 호출)")
    void 설명_생성() {
        for (EligibilityVerdict verdict : EligibilityVerdict.values()) {
            MatchResponse response = matchingService.match(소상공인, verdict, 1);
            if (response.items().isEmpty()) {
                System.out.println("[" + verdict + "] 해당 공고 없음");
                continue;
            }
            ProgramMatch match = response.items().get(0);

            long started = System.currentTimeMillis();
            ExplanationResponse explanation = explanationService.explain(match);
            long elapsed = System.currentTimeMillis() - started;

            System.out.println("=".repeat(78));
            System.out.printf("[%s] %s%n", verdict, match.name());
            System.out.printf("  근거 %d/6  마감 D-%s%n", match.judgedCount(), match.daysLeft());
            match.conditions().stream()
                    .filter(c -> c.verdict() != com.fundcompass.matching.domain.ConditionVerdict.UNKNOWN)
                    .forEach(c -> System.out.printf("    %-10s %-10s 요구:%s / 내값:%s%n",
                            c.label(), c.verdict(), c.requirement(), c.actual()));
            System.out.println("-".repeat(78));
            System.out.printf("  생성됨=%s  %,dms%n", explanation.generated(), elapsed);
            System.out.println("  " + (explanation.explanation() == null
                    ? explanation.note() : explanation.explanation().replace("\n", "\n  ")));
            System.out.println("=".repeat(78));
        }
    }
}
