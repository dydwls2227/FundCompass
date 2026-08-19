package com.fundcompass.matching.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 공고의 지역 문자열을 프로필과 비교 가능한 형태로 맞춘다.
 *
 * <p>실측: `regions`가 SPECIFIED인 공고에서 <b>고유값 458개 / 총 출현 1,460회</b>.
 * 같은 지역이 여러 표기로 나온다 — {@code 충청북도}(29) 와 {@code 충북}(22),
 * {@code 울산광역시}(20) 와 {@code 울산}(29) 와 {@code 대구시}(10).
 * 문자열 그대로 비교하면 절반 가까이 놓친다.
 *
 * <p><b>시군구 → 시도 매핑 테이블(약 250개)은 만들지 않는다.</b> 프로필이 시도와 시군구를
 * 둘 다 가지므로, 공고 값이 시도면 시도끼리·시군구면 시군구끼리 비교하면 충분하다.
 * 매핑 테이블은 유지보수 부담만 크고 판정에 기여하지 않는다.
 */
@Component
public class RegionNormalizer {

    /** 제한이 없다는 뜻의 값. 실측 {@code 국내} 35회, {@code 전국} 13회 */
    private static final Set<String> NATIONWIDE = Set.of("전국", "국내", "전국일원", "제한없음");

    /** 시도 별칭 -> 정식명. 실측에 나온 표기를 모두 넣었다 */
    private static final Map<String, String> PROVINCE_ALIAS = Map.ofEntries(
            Map.entry("서울", "서울특별시"), Map.entry("서울시", "서울특별시"),
            Map.entry("부산", "부산광역시"), Map.entry("부산시", "부산광역시"),
            Map.entry("대구", "대구광역시"), Map.entry("대구시", "대구광역시"),
            Map.entry("인천", "인천광역시"), Map.entry("인천시", "인천광역시"),
            Map.entry("광주", "광주광역시"), Map.entry("광주시", "광주광역시"),
            Map.entry("대전", "대전광역시"), Map.entry("대전시", "대전광역시"),
            Map.entry("울산", "울산광역시"), Map.entry("울산시", "울산광역시"),
            Map.entry("세종", "세종특별자치시"), Map.entry("세종시", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("강원", "강원특별자치도"), Map.entry("강원도", "강원특별자치도"),
            Map.entry("충북", "충청북도"), Map.entry("충남", "충청남도"),
            Map.entry("전북", "전북특별자치도"), Map.entry("전라북도", "전북특별자치도"),
            Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"), Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도"), Map.entry("제주도", "제주특별자치도"));

    private static final Set<String> PROVINCES = Set.of(
            "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시",
            "울산광역시", "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도",
            "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도");

    /** 어느 시의 구인지 알 수 없는 단독 구 이름. 실측 {@code 서구} 5회 */
    private static final Set<String> AMBIGUOUS_DISTRICTS = Set.of(
            "서구", "동구", "남구", "북구", "중구", "중앙구");

    public enum Kind {
        /** 전국·국내 — 지역 제한이 없다 */
        NATIONWIDE,
        /** 시도 단위 */
        PROVINCE,
        /** 시군구 단위 */
        CITY,
        /** 해석 실패. 판정하지 않는다 */
        UNRESOLVED
    }

    public record Region(Kind kind, String value) {}

    public Region normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Region(Kind.UNRESOLVED, null);
        }
        String text = raw.replaceAll("\\s", "");

        if (NATIONWIDE.contains(text)) {
            return new Region(Kind.NATIONWIDE, text);
        }
        String canonical = PROVINCE_ALIAS.get(text);
        if (canonical != null) {
            return new Region(Kind.PROVINCE, canonical);
        }
        if (PROVINCES.contains(text)) {
            return new Region(Kind.PROVINCE, text);
        }
        // 단독 구 이름은 어느 시인지 알 수 없어 판정에 쓸 수 없다
        if (AMBIGUOUS_DISTRICTS.contains(text)) {
            return new Region(Kind.UNRESOLVED, text);
        }
        if (text.endsWith("시") || text.endsWith("군") || text.endsWith("구")) {
            return new Region(Kind.CITY, text);
        }
        // 실측의 `비수도권`, `전남광주통합특별시` 처럼 우리가 모르는 표현
        return new Region(Kind.UNRESOLVED, text);
    }
}
