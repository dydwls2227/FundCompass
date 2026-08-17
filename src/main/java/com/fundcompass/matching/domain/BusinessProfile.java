package com.fundcompass.matching.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 판정에 쓰는 사업자 프로필. F3(입력 폼)의 산출물이자 F4의 입력이다.
 *
 * <p><b>모든 필드가 nullable이다.</b> 비로그인으로도 핵심 가치를 끝까지 경험할 수 있어야 하므로
 * (기획서 F11 원칙) 부분 입력으로도 판정이 돌아가야 한다. 빠진 값은
 * {@link UnknownReason#PROFILE_MISSING}이 되고, 그 조건만 확인필요로 흘러간다.
 *
 * <p>업력은 년수가 아니라 <b>창업일</b>로 받는다. 년수로 받으면 F10("언제 가능해지나")에서
 * 충족 시점을 계산할 수 없다 — 부적격 사유가 시간으로 해소되는지 알려면 기준일이 필요하다.
 *
 * @param foundedOn     창업일. 업력 판정과 F10 시점 안내에 쓴다
 * @param revenueBand   연매출 구간 (F13 — 정확한 금액을 받지 않는다)
 * @param employeeCount 상시근로자 수
 * @param province      시도. 정식명·약칭 모두 허용 ({@code 충청북도} / {@code 충북})
 * @param city          시군구 ({@code 진주시}, {@code 괴산군}, {@code 서구})
 * @param industryName  업종명. 정규화 미구현이라 현재는 판정하지 않는다
 * @param businessType  사업자 유형 ({@code 소상공인}, {@code 중소기업}, {@code 예비창업자} …)
 */
public record BusinessProfile(
        LocalDate foundedOn,
        RevenueBand revenueBand,
        Integer employeeCount,
        String province,
        String city,
        String industryName,
        String businessType
) {

    /** 기준일 시점의 업력(년, 소수). 창업일이 없으면 {@code null} */
    public Double businessAgeYears(LocalDate asOf) {
        if (foundedOn == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(foundedOn, asOf) / 365.25;
    }

    /** 예비창업자 등 아직 창업하지 않은 상태 */
    public boolean notFoundedYet() {
        return foundedOn == null;
    }
}
