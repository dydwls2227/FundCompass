package com.fundcompass.matching.service;

import com.fundcompass.matching.domain.BusinessProfile;
import com.fundcompass.matching.domain.ConditionResult;
import com.fundcompass.matching.domain.EligibilityVerdict;
import com.fundcompass.matching.domain.MatchResult;
import com.fundcompass.matching.domain.RevenueBand;
import com.fundcompass.matching.domain.UnknownReason;
import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.ProgramEligibility;
import com.fundcompass.program.repository.ProgramEligibilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 저장된 1,447건 전체에 판정을 돌려 분포를 본다. LLM 호출 0회.
 *
 * <p>설계가 맞는지는 실측으로만 알 수 있다. 특히 확인하려는 것:
 * <ul>
 *   <li>{@code NEEDS_REVIEW}가 실제로 몇 %인가 (예상: 대부분)</li>
 *   <li>사용자가 해소할 수 있는 미상과 그렇지 않은 미상의 비율</li>
 *   <li>정규화 실패({@code NOT_NORMALIZABLE})가 얼마나 되는가 — 개선 여지의 크기</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EligibilityMatcherManualTest {

    @Autowired ProgramEligibilityRepository eligibilityRepository;
    @Autowired EligibilityMatcher matcher;

    /** 기획서 §2의 1순위 타겟: 창업 3년 이내 소상공인 */
    private static final BusinessProfile 소상공인 = new BusinessProfile(
            LocalDate.of(2024, 3, 1), RevenueBand.UNDER_3, 3,
            "경상북도", "포항시", "음식점업", "소상공인");

    /** 2순위: 운영 중인 중소기업 */
    private static final BusinessProfile 중소기업 = new BusinessProfile(
            LocalDate.of(2015, 5, 20), RevenueBand.UNDER_80, 45,
            "서울특별시", "강남구", "제조업", "중소기업");

    /** 프로필을 거의 안 낸 비로그인 사용자 */
    private static final BusinessProfile 최소입력 = new BusinessProfile(
            null, null, null, "경기도", null, null, "소상공인");

    @Test
    @DisplayName("1,447건 전체 판정 분포 (LLM 호출 없음)")
    void 판정_분포() {
        List<ProgramEligibility> rows = eligibilityRepository.findAll().stream()
                .filter(r -> r.getStatus() == ExtractionStatus.EXTRACTED)
                .filter(r -> r.getExtraction() != null)
                .toList();

        report("소상공인 (창업 2.4년, 매출 1~3억, 3명, 경북 포항)", rows, 소상공인);
        report("중소기업 (업력 11년, 매출 30~80억, 45명, 서울 강남)", rows, 중소기업);
        report("최소입력 (지역·유형만, 경기도 소상공인)", rows, 최소입력);
    }

    private void report(String title, List<ProgramEligibility> rows, BusinessProfile profile) {
        LocalDate asOf = LocalDate.now();
        Map<EligibilityVerdict, Integer> verdicts = new EnumMap<>(EligibilityVerdict.class);
        Map<UnknownReason, Integer> reasons = new EnumMap<>(UnknownReason.class);
        int satisfied = 0, violated = 0, actionable = 0, timeResolvable = 0;
        Map<String, Integer> violationsByField = new java.util.LinkedHashMap<>();

        for (ProgramEligibility row : rows) {
            MatchResult result = matcher.match(row, profile, asOf);
            verdicts.merge(result.verdict(), 1, Integer::sum);
            satisfied += result.satisfied().size();
            violated += result.violated().size();
            actionable += result.actionable().size();
            if (result.eligibleFrom().isPresent()) {
                timeResolvable++;
            }
            for (ConditionResult c : result.conditions()) {
                if (c.unknownReason() != null) {
                    reasons.merge(c.unknownReason(), 1, Integer::sum);
                }
            }
            result.violated().forEach(c -> violationsByField.merge(c.label(), 1, Integer::sum));
        }

        int total = rows.size();
        System.out.println("=".repeat(78));
        System.out.printf("[%s]  공고 %,d건%n", title, total);
        System.out.println("-".repeat(78));
        for (EligibilityVerdict v : EligibilityVerdict.values()) {
            int n = verdicts.getOrDefault(v, 0);
            System.out.printf("  %-14s %5d  (%.1f%%)%n", v, n, pct(n, total));
        }
        System.out.printf("%n  조건 충족 %,d / 위반 %,d%n", satisfied, violated);
        System.out.printf("  사용자가 해소 가능한 미상 %,d개  (공고당 %.2f)%n",
                actionable, (double) actionable / total);
        System.out.printf("  F10 시점 안내 가능 %,d건%n", timeResolvable);
        System.out.println("-".repeat(78));
        System.out.println("  위반 조건별:");
        violationsByField.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("    %-14s %5d%n", e.getKey(), e.getValue()));
        System.out.println("  미상 사유별:");
        for (UnknownReason r : UnknownReason.values()) {
            int n = reasons.getOrDefault(r, 0);
            if (n > 0) {
                System.out.printf("    %-26s %5d%n", r, n);
            }
        }
        System.out.println("=".repeat(78));
    }

    private double pct(int part, int total) {
        return total == 0 ? 0 : 100.0 * part / total;
    }
}
