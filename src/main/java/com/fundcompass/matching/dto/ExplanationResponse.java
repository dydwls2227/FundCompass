package com.fundcompass.matching.dto;

/**
 * F5 설명 생성 결과.
 *
 * @param programId   공고
 * @param explanation 자연어 설명. 설명 생성이 꺼져 있거나 실패하면 {@code null}
 * @param generated   실제로 LLM이 생성했는지. {@code false}면 판정 결과만 보면 된다
 * @param note        생성하지 못한 이유 (키 미설정, 호출 실패 등)
 */
public record ExplanationResponse(
        Long programId,
        String explanation,
        boolean generated,
        String note
) {
    public static ExplanationResponse of(Long programId, String explanation) {
        return new ExplanationResponse(programId, explanation, true, null);
    }

    public static ExplanationResponse unavailable(Long programId, String note) {
        return new ExplanationResponse(programId, null, false, note);
    }
}
