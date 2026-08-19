package com.fundcompass.matching.domain;

/**
 * 연매출 구간 (단위: 억원). 기획서 F13 — <b>정확한 금액이 아니라 구간으로 받는다.</b>
 *
 * <p>매출은 개인정보이자 사업 기밀이고, 주최가 금융보안원이라 데이터 취급 방식 자체가 평가 대상이다.
 * 판정에 필요한 정밀도는 구간으로 충분하다.
 *
 * <p>대가가 하나 있다: 구간이 조건 경계를 걸치면 판정할 수 없다
 * ({@link UnknownReason#BAND_STRADDLES_BOUNDARY}). 예를 들어 {@code UNDER_3}(1~3억)
 * 프로필은 {@code 2억 이하} 조건에 대해 충족인지 위반인지 알 수 없다.
 * 정확한 금액을 받으면 사라지는 문제이므로, <b>어느 쪽을 택했는지 아는 상태로 남긴다.</b>
 *
 * @param lower 하한(포함). 최저 구간은 0
 * @param upper 상한(미포함). 최고 구간은 {@code null} = 무제한
 */
public enum RevenueBand {
    UNDER_1("1억원 미만", 0.0, 1.0),
    UNDER_3("1억원 이상 3억원 미만", 1.0, 3.0),
    UNDER_10("3억원 이상 10억원 미만", 3.0, 10.0),
    UNDER_30("10억원 이상 30억원 미만", 10.0, 30.0),
    UNDER_80("30억원 이상 80억원 미만", 30.0, 80.0),
    UNDER_120("80억원 이상 120억원 미만", 80.0, 120.0),
    OVER_120("120억원 이상", 120.0, null);

    private final String label;
    private final double lower;
    private final Double upper;

    RevenueBand(String label, double lower, Double upper) {
        this.label = label;
        this.lower = lower;
        this.upper = upper;
    }

    public String label() {
        return label;
    }

    public double lower() {
        return lower;
    }

    /** 상한(미포함). 무제한이면 {@code null} */
    public Double upper() {
        return upper;
    }
}
