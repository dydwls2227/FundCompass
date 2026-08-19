package com.fundcompass.matching.service;

import com.fundcompass.matching.domain.ConditionResult;
import com.fundcompass.matching.domain.EligibilityVerdict;
import com.fundcompass.matching.dto.ProgramMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프롬프트 조립만 검사한다. LLM 호출 0회, Spring 컨텍스트도 띄우지 않는다.
 *
 * <p>확인하려는 것은 <b>보내는 정보가 최소인가</b>이다. F5는 사용자의 사업자 정보가 나가는
 * 경로이므로 필요 없는 것이 섞이면 안 된다.
 */
class ExplanationServiceTest {

    private final ExplanationService service = new ExplanationService(null);

    private ProgramMatch match() {
        return new ProgramMatch(955L, "대전 인쇄소공인 컨설팅", "대전광역시", "https://example",
                LocalDate.of(2026, 9, 30), 43, EligibilityVerdict.INELIGIBLE, 3,
                List.of(
                        ConditionResult.satisfied("regions", "지역", "대전광역시", "대전광역시 동구", "ㅇ 대전 동구"),
                        ConditionResult.violated("employeeCount", "고용", "10명 미만", "12명", "상시근로자 10명 미만"),
                        ConditionResult.unknown("annualRevenue", "매출",
                                com.fundcompass.matching.domain.UnknownReason.NOT_STATED, null, null, null)),
                List.of(), null);
    }

    @Test
    @DisplayName("판정 결과만 담고 공고 원문·프로필 원본은 넣지 않는다")
    void 프롬프트_최소화() {
        String prompt = service.buildPrompt(match());
        System.out.println("-".repeat(60));
        System.out.println(prompt);
        System.out.println("-".repeat(60));

        assertThat(prompt).contains("대전 인쇄소공인 컨설팅", "신청 불가");
        assertThat(prompt).contains("고용", "10명 미만", "12명");
        // 공고가 언급하지 않은 조건은 프롬프트에 넣지 않는다 — 사용자에게 할 일이 아니다
        assertThat(prompt).doesNotContain("매출");
        // 원문 근거는 설명에 필요 없다. 화면이 이미 보여준다
        assertThat(prompt).doesNotContain("상시근로자 10명 미만");
    }

    @Test
    @DisplayName("키가 없으면 설명 없이 판정 결과만 쓰라고 알린다")
    void 키_없음() {
        var response = service.explain(match());
        assertThat(response.generated()).isFalse();
        assertThat(response.explanation()).isNull();
        assertThat(response.note()).contains("조건별 판정 결과");
        System.out.println("키 미설정 응답: " + response.note());
    }
}
