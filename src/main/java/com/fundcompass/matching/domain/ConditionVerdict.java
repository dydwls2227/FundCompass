package com.fundcompass.matching.domain;

/** 조건 하나에 대한 판정 */
public enum ConditionVerdict {
    /** 프로필이 조건을 충족한다 */
    SATISFIED,
    /** 프로필이 조건을 명확히 위반한다. 근거가 있으므로 부적격의 사유가 된다 */
    VIOLATED,
    /** 판정할 수 없다. 사유는 {@link UnknownReason} 참조 */
    UNKNOWN
}
