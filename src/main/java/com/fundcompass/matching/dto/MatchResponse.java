package com.fundcompass.matching.dto;

import com.fundcompass.matching.domain.EligibilityVerdict;

import java.util.List;
import java.util.Map;

/**
 * 매칭 결과 응답. 기획서 §4의 <b>"적격 N건 / 확인필요 M건 / 부적격 K건 탭"</b> 화면에 대응한다.
 *
 * <p>{@code counts}는 항상 전체 집계를 담고 {@code items}는 요청한 탭의 목록만 담는다.
 * 부적격이 실측상 60% 넘게 나오므로(지자체 사업이 많아 지역 조건에서 걸린다)
 * 세 탭을 한 번에 내려보내면 응답이 불필요하게 커진다.
 *
 * @param counts       판정별 전체 건수. 탭 배지에 쓴다
 * @param verdict      이 응답의 {@code items}가 어느 판정인지. 전체면 {@code null}
 * @param items        해당 판정의 공고 목록 (마감 임박순)
 * @param totalMatched 판정을 시도한 공고 수
 * @param excludedClosed 이미 마감돼 제외한 공고 수
 */
public record MatchResponse(
        Map<EligibilityVerdict, Integer> counts,
        EligibilityVerdict verdict,
        List<ProgramMatch> items,
        int totalMatched,
        int excludedClosed
) {}
