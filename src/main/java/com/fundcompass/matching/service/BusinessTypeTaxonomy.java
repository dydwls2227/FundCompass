package com.fundcompass.matching.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사업자 유형의 포함 관계를 판정한다. {@code businessTypes}는 판정률 93.4%로 가장 잘 뽑히는 조건이다.
 *
 * <p><b>문자열 비교로는 안 된다.</b> 실측 1위가 {@code 중소기업}(728회)인데
 * {@code 소상공인}(183회) 사용자는 중소기업 요건을 충족한다 —
 * {@code 소공인 ⊂ 소상공인 ⊂ 소기업 ⊂ 중소기업} 이기 때문이다.
 *
 * <h2>🚨 이 필드는 축이 섞여 있다</h2>
 * 실측 어휘를 보면 한 필드에 서로 다른 분류축이 뒤섞여 있다.
 * <pre>
 *   규모      중소기업 728, 중견기업 189, 소상공인 183, 대기업 17, 소기업 20
 *   법적형태  개인사업자 42, 법인사업자 21, 법인 14, 개인 14
 *   상태      예비창업자 42, 창업기업 40, 스타트업 13, 창업자 6
 *   성격      사회적기업 29, 협동조합 22, 마을기업 20, 비영리법인 12
 *   비기업    대학 23, 기관 20, 공공기관 16, 연구기관 10, 단체 10
 *   업종(!)   여행사 11, 제조기업 10, 여행업체 6, 수출기업 5
 * </pre>
 * 마지막 줄은 사업자 유형이 아니라 업종이며, 추출 단계의 품질 문제다.
 * 따라서 <b>하나의 깔끔한 계층을 만들 수 없다.</b> 규모 축만 계층으로 다루고
 * 나머지는 정확히 일치할 때만 인정한다. 억지로 계층을 만들면 틀린 매칭이 나온다.
 *
 * <p>{@code 기업}(76), {@code 기관}(20)처럼 정보량이 없는 값은 <b>어떤 사업자든 충족</b>으로 본다 —
 * 실제로 그 공고들이 유형을 제한하지 않는다는 뜻이다.
 */
@Component
public class BusinessTypeTaxonomy {

    /** 값 -> 그 값을 충족시키는 프로필 유형들. 좁은 쪽이 넓은 요건을 만족한다 */
    private static final Map<String, Set<String>> SATISFIED_BY = Map.ofEntries(
            Map.entry("중소기업", Set.of("중소기업", "소기업", "소상공인", "소공인",
                    "벤처기업", "창업기업", "스타트업", "중소벤처기업")),
            Map.entry("소기업", Set.of("소기업", "소상공인", "소공인")),
            Map.entry("소상공인", Set.of("소상공인", "소공인")),
            Map.entry("소공인", Set.of("소공인")),
            Map.entry("중견기업", Set.of("중견기업")),
            Map.entry("대기업", Set.of("대기업")),
            Map.entry("중소벤처기업", Set.of("중소벤처기업", "벤처기업", "중소기업")),
            Map.entry("벤처기업", Set.of("벤처기업")),
            Map.entry("창업기업", Set.of("창업기업", "스타트업")),
            Map.entry("스타트업", Set.of("스타트업", "창업기업")),
            Map.entry("예비창업자", Set.of("예비창업자", "창업자")),
            Map.entry("창업자", Set.of("창업자", "예비창업자")),
            Map.entry("개인사업자", Set.of("개인사업자", "개인", "소상공인", "소공인")),
            Map.entry("개인", Set.of("개인", "개인사업자")),
            Map.entry("법인사업자", Set.of("법인사업자", "법인")),
            Map.entry("법인", Set.of("법인", "법인사업자")),
            Map.entry("사회적경제기업", Set.of("사회적경제기업", "사회적기업", "예비사회적기업",
                    "협동조합", "사회적협동조합", "마을기업", "자활기업")),
            Map.entry("사회적기업", Set.of("사회적기업", "예비사회적기업")),
            Map.entry("협동조합", Set.of("협동조합", "사회적협동조합")));

    /** 유형을 제한하지 않는 것과 같은 값. 실측 {@code 기업} 76회, {@code 기관} 20회 */
    private static final Set<String> UNRESTRICTIVE = Set.of("기업", "기관", "단체", "사업자", "업체");

    /**
     * 사업자 유형이 아니라 업종·인증인 값. 이 필드로는 판정하지 않는다.
     * 추출 품질 문제이므로 프롬프트 개선 대상이기도 하다.
     */
    private static final Set<String> NOT_A_BUSINESS_TYPE = Set.of(
            "여행사", "여행업체", "제조기업", "수출기업", "우선지원대상기업",
            "경영혁신형 중소기업", "연구소", "연구기관");

    public enum Match {
        /** 프로필이 이 요건을 충족한다 */
        SATISFIED,
        /** 프로필이 이 요건을 충족하지 않는다 */
        NOT_SATISFIED,
        /** 우리가 해석할 수 없는 값이라 판정하지 않는다 */
        UNRESOLVED
    }

    /**
     * 공고가 허용하는 유형 목록 중 <b>하나라도</b> 프로필이 충족하면 SATISFIED다.
     * 목록은 OR 조건이다 — "중소기업 또는 소상공인"에 소상공인이 해당하면 통과다.
     */
    public Match match(List<String> required, String profileType) {
        if (required == null || required.isEmpty()) {
            return Match.UNRESOLVED;
        }
        if (profileType == null || profileType.isBlank()) {
            return Match.UNRESOLVED;
        }
        String profile = profileType.replaceAll("\\s", "");

        boolean anyResolved = false;
        for (String raw : required) {
            String value = raw == null ? "" : raw.replaceAll("\\s", "");

            if (UNRESTRICTIVE.contains(value)) {
                return Match.SATISFIED;      // 사실상 제한이 없다
            }
            if (NOT_A_BUSINESS_TYPE.contains(value)) {
                continue;                    // 이 값으로는 판정하지 않는다
            }
            Set<String> satisfiedBy = SATISFIED_BY.get(value);
            if (satisfiedBy == null) {
                continue;                    // 모르는 값 — 다른 값으로 판정을 시도한다
            }
            anyResolved = true;
            if (satisfiedBy.contains(profile)) {
                return Match.SATISFIED;
            }
        }
        // 해석 가능한 값이 하나라도 있었는데 전부 안 맞으면 진짜 미충족이다.
        // 하나도 해석하지 못했으면 판정 자체를 보류한다
        return anyResolved ? Match.NOT_SATISFIED : Match.UNRESOLVED;
    }
}
