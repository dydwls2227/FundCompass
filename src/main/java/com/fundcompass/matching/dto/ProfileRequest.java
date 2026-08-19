package com.fundcompass.matching.dto;

import com.fundcompass.matching.domain.BusinessProfile;
import com.fundcompass.matching.domain.RevenueBand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * F3 사업자 프로필 입력. 기획서 F11 원칙에 따라 <b>모든 필드가 선택</b>이다 —
 * 비로그인 사용자가 부분 입력으로도 매칭 결과를 끝까지 볼 수 있어야 한다.
 *
 * <p>다만 실측상 판정에 기여하는 정도가 크게 다르다. 판정률이
 * {@code businessTypes} 93.4% / {@code regions} 74.6%이므로
 * <b>사업자 유형과 지역만 넣어도 대부분의 공고가 판정된다.</b>
 * 매출(8.5%)·고용(13.4%)·업력(18.0%)은 있으면 좋지만 없어도 결과가 크게 달라지지 않는다.
 *
 * <p>서버는 이 값을 <b>저장하지 않는다</b>. 기획서 F13 — *"비로그인 데이터는 서버 영구 저장 없이
 * 세션 범위로 한정"* — 인데, 요청마다 프로필을 받아 판정만 하고 버리면 세션조차 필요 없다.
 * 로그인 사용자의 프로필 저장은 F12에서 별도로 다룬다.
 *
 * @param foundedOn     창업일. 없으면 예비창업자이거나 미입력
 * @param revenueBand   연매출 구간 (F13 — 정확한 금액을 받지 않는다)
 * @param employeeCount 상시근로자 수
 * @param province      시도. {@code 충청북도} / {@code 충북} 모두 허용
 * @param city          시군구
 * @param industryName  업종명. 현재 판정에 쓰이지 않는다 (KSIC 미구현)
 * @param businessType  사업자 유형. {@code 소상공인}, {@code 중소기업}, {@code 예비창업자} …
 */
public record ProfileRequest(

        @PastOrPresent(message = "창업일은 미래일 수 없습니다")
        LocalDate foundedOn,

        RevenueBand revenueBand,

        @Min(value = 0, message = "상시근로자 수는 0명 이상이어야 합니다")
        @Max(value = 100_000, message = "상시근로자 수를 확인해 주세요")
        Integer employeeCount,

        @Size(max = 20, message = "시도명이 너무 깁니다")
        String province,

        @Size(max = 30, message = "시군구명이 너무 깁니다")
        String city,

        @Size(max = 60, message = "업종명이 너무 깁니다")
        String industryName,

        @Size(max = 40, message = "사업자 유형이 너무 깁니다")
        String businessType
) {

    public BusinessProfile toProfile() {
        return new BusinessProfile(foundedOn, revenueBand, employeeCount,
                blankToNull(province), blankToNull(city),
                blankToNull(industryName), blankToNull(businessType));
    }

    /** 폼에서 빈 문자열이 오면 "미입력"과 같게 다룬다 */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
