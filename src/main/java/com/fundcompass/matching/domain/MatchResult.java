package com.fundcompass.matching.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 공고 하나에 대한 판정 결과 전체.
 *
 * <p>{@link #verdict()}는 기획서의 3분류지만 <b>실측상 대부분 {@code NEEDS_REVIEW}</b>다.
 * 실질적인 정보는 {@link #conditions()}에 있고, UI는 그쪽을 주인공으로 삼아야 한다.
 */
public record MatchResult(
        Long programId,
        EligibilityVerdict verdict,
        List<ConditionResult> conditions
) {

    /**
     * 조건별 결과에서 판정을 도출한다. 기획서 §4 로직 그대로다.
     *
     * <p>순서가 중요하다. 위반을 먼저 보는 이유는, 근거가 있는 판정이 없는 판정보다
     * 강하기 때문이다 — 미상이 남아 있어도 명확한 위반이 하나 있으면 부적격이다.
     */
    public static MatchResult of(Long programId, List<ConditionResult> conditions) {
        boolean hasViolation = conditions.stream()
                .anyMatch(c -> c.verdict() == ConditionVerdict.VIOLATED);
        // NOT_STATED는 제약 없음으로 보므로 적격을 막지 않는다. 이 구분이 없으면
        // 모든 공고에 NOT_STATED가 하나씩 있어 ELIGIBLE이 영구히 발생하지 않는다
        boolean hasBlocker = conditions.stream().anyMatch(ConditionResult::blocksEligibility);
        boolean hasSatisfied = conditions.stream()
                .anyMatch(c -> c.verdict() == ConditionVerdict.SATISFIED);

        EligibilityVerdict verdict;
        if (hasViolation) {
            verdict = EligibilityVerdict.INELIGIBLE;
        } else if (hasBlocker || !hasSatisfied) {
            // 충족이 하나도 없으면 "전부 미상"이므로 적격이라고 말할 근거가 없다
            verdict = EligibilityVerdict.NEEDS_REVIEW;
        } else {
            verdict = EligibilityVerdict.ELIGIBLE;
        }
        return new MatchResult(programId, verdict, conditions);
    }

    public List<ConditionResult> satisfied() {
        return by(ConditionVerdict.SATISFIED);
    }

    public List<ConditionResult> violated() {
        return by(ConditionVerdict.VIOLATED);
    }

    /** 사용자가 입력해서 해소할 수 있는 미상만. 화면의 "할 일" 목록 */
    public List<ConditionResult> actionable() {
        return conditions.stream().filter(ConditionResult::actionableByUser).toList();
    }

    /** 공고가 애초에 따지지 않는 조건. 표시는 하되 행동을 요구하지 않는다 */
    public List<ConditionResult> notStated() {
        return conditions.stream()
                .filter(c -> c.unknownReason() == UnknownReason.NOT_STATED
                        || c.unknownReason() == UnknownReason.EVIDENCE_UNTRUSTED)
                .toList();
    }

    /**
     * F10 — 시간이 지나면 신청할 수 있게 되는 시점. 가장 늦은 조건을 기준으로 한다.
     * 시간으로 해소되지 않는 위반이 있으면 빈 값이다.
     */
    public Optional<LocalDate> eligibleFrom() {
        if (violated().stream().anyMatch(c -> c.eligibleFrom() == null)) {
            return Optional.empty();   // 시간으로 안 풀리는 위반이 섞여 있다
        }
        return violated().stream()
                .map(ConditionResult::eligibleFrom)
                .max(Comparator.naturalOrder());
    }

    /** 판정에 실제로 쓰인 조건 수. 근거가 얼마나 충실한지를 나타낸다 */
    public int judgedCount() {
        return (int) conditions.stream()
                .filter(c -> c.verdict() != ConditionVerdict.UNKNOWN)
                .count();
    }

    private List<ConditionResult> by(ConditionVerdict verdict) {
        return conditions.stream().filter(c -> c.verdict() == verdict).toList();
    }
}
