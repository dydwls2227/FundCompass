package com.fundcompass.matching.domain;

import java.time.LocalDate;

/**
 * 조건 하나의 판정 결과. 기획서가 요구한 <b>"조건별 충족 여부 체크리스트"</b>의 한 줄이다.
 *
 * <p>판정만 담지 않고 근거({@code evidence})와 사람이 읽을 설명({@code message})을 함께 싣는다.
 * 기획서 원칙 — *"판정 근거는 구조화된 데이터로 반환한다. LLM 설명(F5)은 그 위에 얹는 표현 계층"* —
 * 이므로 F5는 이 record를 문장으로 풀어 쓸 뿐이고 판정을 다시 하지 않는다.
 *
 * @param field         조건 이름 ({@code businessAge}, {@code regions} …)
 * @param label         화면에 쓸 한글 이름 ({@code 업력}, {@code 지역})
 * @param verdict       판정
 * @param unknownReason {@code UNKNOWN}일 때의 사유. 아니면 {@code null}
 * @param requirement   공고가 요구하는 것 ({@code 업력 3년 이상})
 * @param actual        프로필 값 ({@code 1.4년}). 값이 없으면 {@code null}
 * @param evidence      공고 원문 인용. F5·F7이 쓴다
 * @param eligibleFrom  F10 — 시간이 지나면 충족되는 경우의 그 시점. 아니면 {@code null}
 */
public record ConditionResult(
        String field,
        String label,
        ConditionVerdict verdict,
        UnknownReason unknownReason,
        String requirement,
        String actual,
        String evidence,
        LocalDate eligibleFrom
) {

    public static ConditionResult satisfied(String field, String label,
                                            String requirement, String actual, String evidence) {
        return new ConditionResult(field, label, ConditionVerdict.SATISFIED, null,
                requirement, actual, evidence, null);
    }

    public static ConditionResult violated(String field, String label,
                                           String requirement, String actual, String evidence) {
        return new ConditionResult(field, label, ConditionVerdict.VIOLATED, null,
                requirement, actual, evidence, null);
    }

    /** 시간이 지나면 충족되는 위반. F10 시점 안내용 */
    public static ConditionResult violatedUntil(String field, String label, String requirement,
                                                String actual, String evidence, LocalDate from) {
        return new ConditionResult(field, label, ConditionVerdict.VIOLATED, null,
                requirement, actual, evidence, from);
    }

    public static ConditionResult unknown(String field, String label, UnknownReason reason,
                                          String requirement, String actual, String evidence) {
        return new ConditionResult(field, label, ConditionVerdict.UNKNOWN, reason,
                requirement, actual, evidence, null);
    }

    /**
     * 사용자가 입력해서 해소할 수 있는 미상인가.
     * 이것만 화면에서 할 일로 표시한다 — 나머지 미상은 사용자가 손댈 수 없다.
     */
    public boolean actionableByUser() {
        return unknownReason == UnknownReason.PROFILE_MISSING
                || unknownReason == UnknownReason.BAND_STRADDLES_BOUNDARY;
    }

    /**
     * 적격 판정을 막는 미상인가.
     *
     * <p>{@link UnknownReason#NOT_STATED}만 막지 않는다 — 공고가 조건을 걸지 않았으므로
     * 제약이 없는 것으로 본다. 나머지는 <b>조건이 존재한다는 것을 알면서</b> 값을 다루지
     * 못하는 경우이므로 적격이라고 말할 근거가 없다.
     */
    public boolean blocksEligibility() {
        return verdict == ConditionVerdict.UNKNOWN
                && unknownReason != UnknownReason.NOT_STATED;
    }
}
