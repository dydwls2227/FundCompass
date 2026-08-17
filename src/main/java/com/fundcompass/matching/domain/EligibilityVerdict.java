package com.fundcompass.matching.domain;

/**
 * 공고 하나에 대한 최종 판정. 기획서 §4의 3분류를 그대로 따른다.
 *
 * <pre>
 *   모든 조건 충족           -> ELIGIBLE
 *   하나라도 명확히 불충족    -> INELIGIBLE
 *   판단 불가한 조건 존재     -> NEEDS_REVIEW
 * </pre>
 *
 * <p>⚠️ <b>실측상 대부분이 {@link #NEEDS_REVIEW}로 떨어진다.</b> 6개 조건이 모두 판정된 공고가
 * 1,447건 중 9건(0.6%)뿐이기 때문이다. 그래서 이 enum 하나만 보여주면 화면이 전부 "확인필요"가 된다.
 *
 * <p>실질적인 정보는 {@link MatchResult}가 함께 싣는 <b>조건별 상세</b>에 있다
 * (기획서가 요구한 *"확인필요 + 무엇을 확인해야 하는지"*, *"조건별 충족 여부 체크리스트"*).
 * UI는 판정 배지가 아니라 체크리스트를 주인공으로 삼아야 한다.
 */
public enum EligibilityVerdict {
    /** 판정된 조건이 하나 이상 있고, 전부 충족이며, 미상이 없다 */
    ELIGIBLE,

    /** 명확히 위반한 조건이 하나 이상 있다. 근거가 있으므로 신뢰할 수 있는 판정이다 */
    INELIGIBLE,

    /** 위반은 없으나 판정하지 못한 조건이 남아 있다 */
    NEEDS_REVIEW
}
