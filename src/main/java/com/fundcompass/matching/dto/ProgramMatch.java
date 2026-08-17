package com.fundcompass.matching.dto;

import com.fundcompass.matching.domain.ConditionResult;
import com.fundcompass.matching.domain.EligibilityVerdict;
import com.fundcompass.matching.domain.MatchResult;
import com.fundcompass.program.domain.Program;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 공고 하나의 매칭 결과. 판정({@link MatchResult})에 사용자가 알아야 할 공고 정보를 붙인다.
 *
 * <p>{@code conditions}가 기획서 §4의 <b>"조건별 충족 여부 체크리스트"</b>이고 화면의 주인공이다.
 * 판정 배지({@code verdict}) 하나만 보여주면 안 된다 — 실측상 대부분이
 * {@code NEEDS_REVIEW}로 떨어지므로 배지만으로는 정보가 없다.
 *
 * @param eligibleFrom  F10 — 시간이 지나면 신청 가능해지는 시점. 아니면 {@code null}
 * @param actionable    사용자가 입력해서 해소할 수 있는 미상. "매출을 입력하면 정확해집니다"
 * @param judgedCount   판정에 실제로 쓰인 조건 수(0~6). 근거가 얼마나 충실한지
 */
public record ProgramMatch(
        Long programId,
        String name,
        String institution,
        String detailUrl,
        LocalDate applyEndDate,
        Integer daysLeft,
        EligibilityVerdict verdict,
        int judgedCount,
        List<ConditionResult> conditions,
        List<ConditionResult> actionable,
        LocalDate eligibleFrom
) {

    public static ProgramMatch of(Program program, MatchResult result, LocalDate asOf) {
        return new ProgramMatch(
                program.getId(),
                program.getName(),
                program.getSupervisingInstitution(),
                program.getDetailUrl(),
                program.getApplyEndDate(),
                daysLeft(program.getApplyEndDate(), asOf),
                result.verdict(),
                result.judgedCount(),
                result.conditions(),
                result.actionable(),
                result.eligibleFrom().orElse(null));
    }

    /** 마감까지 남은 일수. 기한이 없으면 {@code null} — 상시모집이거나 공고에 기한이 없다 */
    private static Integer daysLeft(LocalDate endDate, LocalDate asOf) {
        return endDate == null ? null : (int) ChronoUnit.DAYS.between(asOf, endDate);
    }
}
