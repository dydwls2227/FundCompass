package com.fundcompass.matching.service;

import com.fundcompass.matching.domain.BusinessProfile;
import com.fundcompass.matching.domain.ConditionResult;
import com.fundcompass.matching.domain.MatchResult;
import com.fundcompass.matching.domain.RevenueBand;
import com.fundcompass.matching.domain.UnknownReason;
import com.fundcompass.program.domain.ConditionStatus;
import com.fundcompass.program.domain.ProgramEligibility;
import com.fundcompass.program.infra.dto.EligibilityExtraction;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 프로필 × 공고 자격요건 → 적격 판정. 기획서 F4, 서비스의 심장이다.
 *
 * <p><b>판정은 전부 자바 코드가 한다.</b> 기획서 핵심 원칙 —
 * *"LLM은 무엇을 추천할지 결정하지 않는다. 룰 엔진이 결정하고, LLM은 설명한다"* —
 * 이므로 이 클래스는 LLM을 호출하지 않는다. 판정 근거가 코드로 추적·재현 가능하다.
 *
 * <p>입력은 F2가 뽑아 저장한 {@code extraction}(JSONB)이고, 신뢰할 수 없는 필드는
 * {@code unverified_fields}로 걸러 들어온다.
 */
@Service
@RequiredArgsConstructor
public class EligibilityMatcher {

    private final ObjectMapper objectMapper;
    private final RegionNormalizer regionNormalizer;
    private final BusinessTypeTaxonomy businessTypeTaxonomy;

    public MatchResult match(ProgramEligibility row, BusinessProfile profile, LocalDate asOf) {
        EligibilityExtraction e =
                objectMapper.readValue(row.getExtraction(), EligibilityExtraction.class);
        Set<String> untrusted = Set.copyOf(row.unverifiedFieldList());

        List<ConditionResult> results = new ArrayList<>();
        results.add(businessAge(e.businessAge(), profile, asOf, untrusted));
        results.add(revenue(e.annualRevenue(), profile, untrusted));
        results.add(employees(e.employeeCount(), profile, untrusted));
        results.add(region(e.regions(), profile, untrusted));
        results.add(industry(e.industries(), profile, untrusted));
        results.add(businessType(e.businessTypes(), profile, untrusted));

        return MatchResult.of(row.getProgramId(), results);
    }

    // ── 업력 ─────────────────────────────────────────────────────────────

    private ConditionResult businessAge(EligibilityExtraction.RangeCondition c,
                                        BusinessProfile profile, LocalDate asOf,
                                        Set<String> untrusted) {
        String field = "businessAge";
        String label = "업력";
        ConditionResult skip = skipIfUnusable(field, label, c, untrusted, statusOf(c));
        if (skip != null) {
            return skip;
        }
        Double years = profile.businessAgeYears(asOf);
        if (years == null) {
            return ConditionResult.unknown(field, label, UnknownReason.PROFILE_MISSING,
                    describeRange(c, "년"), null, c.evidence());
        }
        String actual = String.format("%.1f년", years);

        // 하한 미달은 시간이 지나면 해소된다 -> F10 시점 안내
        if (c.min() != null && below(years, c.min(), c.minInclusive())) {
            LocalDate from = profile.foundedOn()
                    .plusDays(Math.round(c.min() * 365.25) + (c.minInclusive() ? 0 : 1));
            return ConditionResult.violatedUntil(field, label,
                    describeRange(c, "년"), actual, c.evidence(), from);
        }
        if (c.max() != null && above(years, c.max(), c.maxInclusive())) {
            // 상한 초과는 시간이 지나도 해소되지 않는다
            return ConditionResult.violated(field, label,
                    describeRange(c, "년"), actual, c.evidence());
        }
        return ConditionResult.satisfied(field, label, describeRange(c, "년"), actual, c.evidence());
    }

    // ── 매출 (구간 판정) ─────────────────────────────────────────────────

    private ConditionResult revenue(EligibilityExtraction.RangeCondition c,
                                    BusinessProfile profile, Set<String> untrusted) {
        String field = "annualRevenue";
        String label = "매출";
        ConditionResult skip = skipIfUnusable(field, label, c, untrusted, statusOf(c));
        if (skip != null) {
            return skip;
        }
        RevenueBand band = profile.revenueBand();
        if (band == null) {
            return ConditionResult.unknown(field, label, UnknownReason.PROFILE_MISSING,
                    describeRange(c, "억원"), null, c.evidence());
        }
        String requirement = describeRange(c, "억원");

        // 구간 전체가 조건 밖이면 위반, 전체가 안이면 충족, 걸치면 판정 보류.
        // 구간으로 받은 대가다(F13) — 정확한 금액이면 이 분기가 없다
        boolean wholeBandFails =
                (c.max() != null && above(band.lower(), c.max(), c.maxInclusive()))
                        || (c.min() != null && band.upper() != null
                        && below(band.upper(), c.min(), c.minInclusive()));
        if (wholeBandFails) {
            return ConditionResult.violated(field, label, requirement, band.label(), c.evidence());
        }
        boolean wholeBandPasses =
                (c.max() == null || (band.upper() != null && !above(band.upper(), c.max(), true)))
                        && (c.min() == null || !below(band.lower(), c.min(), c.minInclusive()));
        if (wholeBandPasses) {
            return ConditionResult.satisfied(field, label, requirement, band.label(), c.evidence());
        }
        return ConditionResult.unknown(field, label, UnknownReason.BAND_STRADDLES_BOUNDARY,
                requirement, band.label(), c.evidence());
    }

    // ── 고용 ─────────────────────────────────────────────────────────────

    private ConditionResult employees(EligibilityExtraction.RangeCondition c,
                                      BusinessProfile profile, Set<String> untrusted) {
        String field = "employeeCount";
        String label = "고용";
        ConditionResult skip = skipIfUnusable(field, label, c, untrusted, statusOf(c));
        if (skip != null) {
            return skip;
        }
        Integer count = profile.employeeCount();
        if (count == null) {
            return ConditionResult.unknown(field, label, UnknownReason.PROFILE_MISSING,
                    describeRange(c, "명"), null, c.evidence());
        }
        String actual = count + "명";
        if ((c.min() != null && below(count, c.min(), c.minInclusive()))
                || (c.max() != null && above(count, c.max(), c.maxInclusive()))) {
            return ConditionResult.violated(field, label,
                    describeRange(c, "명"), actual, c.evidence());
        }
        return ConditionResult.satisfied(field, label, describeRange(c, "명"), actual, c.evidence());
    }

    // ── 지역 ─────────────────────────────────────────────────────────────

    private ConditionResult region(EligibilityExtraction.ListCondition c,
                                   BusinessProfile profile, Set<String> untrusted) {
        String field = "regions";
        String label = "지역";
        ConditionResult skip = skipIfUnusable(field, label, c, untrusted, statusOf(c));
        if (skip != null) {
            return skip;
        }
        String requirement = String.join(", ", c.values());
        String actual = join(profile.province(), profile.city());

        if (profile.province() == null && profile.city() == null) {
            return ConditionResult.unknown(field, label, UnknownReason.PROFILE_MISSING,
                    requirement, null, c.evidence());
        }
        RegionNormalizer.Region myProvince = regionNormalizer.normalize(profile.province());
        String myCity = profile.city() == null ? null : profile.city().replaceAll("\\s", "");

        boolean anyResolved = false;
        for (String raw : c.values()) {
            RegionNormalizer.Region r = regionNormalizer.normalize(raw);
            switch (r.kind()) {
                case NATIONWIDE -> {
                    return ConditionResult.satisfied(field, label, requirement, actual, c.evidence());
                }
                case PROVINCE -> {
                    anyResolved = true;
                    if (r.value().equals(myProvince.value())) {
                        return ConditionResult.satisfied(field, label,
                                requirement, actual, c.evidence());
                    }
                }
                case CITY -> {
                    anyResolved = true;
                    if (r.value().equals(myCity)) {
                        return ConditionResult.satisfied(field, label,
                                requirement, actual, c.evidence());
                    }
                }
                case UNRESOLVED -> { }
            }
        }
        if (!anyResolved) {
            return ConditionResult.unknown(field, label, UnknownReason.NOT_NORMALIZABLE,
                    requirement, actual, c.evidence());
        }
        return ConditionResult.violated(field, label, requirement, actual, c.evidence());
    }

    // ── 업종 (정규화 미구현) ─────────────────────────────────────────────

    private ConditionResult industry(EligibilityExtraction.ListCondition c,
                                     BusinessProfile profile, Set<String> untrusted) {
        String field = "industries";
        String label = "업종";
        ConditionResult skip = skipIfUnusable(field, label, c, untrusted, statusOf(c));
        if (skip != null) {
            return skip;
        }
        // KSIC 코드 체계가 없어 아직 판정하지 않는다. 억지로 문자열을 비교하면
        // "제조업" 요건에 "금속가공제품 제조업" 프로필이 미충족으로 떨어져 틀린 부적격이 나온다
        return ConditionResult.unknown(field, label, UnknownReason.NOT_NORMALIZABLE,
                String.join(", ", c.values()), profile.industryName(), c.evidence());
    }

    // ── 사업자 유형 ──────────────────────────────────────────────────────

    private ConditionResult businessType(EligibilityExtraction.ListCondition c,
                                         BusinessProfile profile, Set<String> untrusted) {
        String field = "businessTypes";
        String label = "사업자 유형";
        ConditionResult skip = skipIfUnusable(field, label, c, untrusted, statusOf(c));
        if (skip != null) {
            return skip;
        }
        String requirement = String.join(", ", c.values());
        if (profile.businessType() == null) {
            return ConditionResult.unknown(field, label, UnknownReason.PROFILE_MISSING,
                    requirement, null, c.evidence());
        }
        return switch (businessTypeTaxonomy.match(c.values(), profile.businessType())) {
            case SATISFIED -> ConditionResult.satisfied(field, label,
                    requirement, profile.businessType(), c.evidence());
            case NOT_SATISFIED -> ConditionResult.violated(field, label,
                    requirement, profile.businessType(), c.evidence());
            case UNRESOLVED -> ConditionResult.unknown(field, label,
                    UnknownReason.NOT_NORMALIZABLE,
                    requirement, profile.businessType(), c.evidence());
        };
    }

    // ── 공통 ─────────────────────────────────────────────────────────────

    /**
     * 판정에 쓸 수 없는 조건을 걸러낸다. 걸리면 {@code ConditionResult}, 아니면 {@code null}.
     *
     * <p>순서가 중요하다. 근거 미신뢰를 먼저 보는 이유는, F2가 값을 뽑았더라도
     * 원문 대조에 실패했으면 그 값으로 판정해선 안 되기 때문이다 —
     * 근거 없는 부적격 판정은 이 서비스에서 가장 나쁜 오류다.
     */
    private ConditionResult skipIfUnusable(String field, String label, Object condition,
                                           Set<String> untrusted, ConditionStatus status) {
        if (untrusted.contains(field)) {
            return ConditionResult.unknown(field, label, UnknownReason.EVIDENCE_UNTRUSTED,
                    null, null, null);
        }
        if (condition == null || status == null || status == ConditionStatus.UNKNOWN) {
            return ConditionResult.unknown(field, label, UnknownReason.NOT_STATED,
                    null, null, null);
        }
        if (status == ConditionStatus.NOT_REQUIRED) {
            // 공고가 "제한 없음"을 명시했다. 미상이 아니라 충족이다
            return ConditionResult.satisfied(field, label, "제한 없음", null, evidenceOf(condition));
        }
        return null;
    }

    private ConditionStatus statusOf(Object condition) {
        if (condition instanceof EligibilityExtraction.RangeCondition r) {
            return r.status();
        }
        if (condition instanceof EligibilityExtraction.ListCondition l) {
            return l.status();
        }
        return null;
    }

    private String evidenceOf(Object condition) {
        if (condition instanceof EligibilityExtraction.RangeCondition r) {
            return r.evidence();
        }
        if (condition instanceof EligibilityExtraction.ListCondition l) {
            return l.evidence();
        }
        return null;
    }

    /** {@code 이상}/{@code 초과}/{@code 이하}/{@code 미만}을 경계 포함 여부에 맞춰 문장으로 */
    private String describeRange(EligibilityExtraction.RangeCondition c, String unit) {
        StringBuilder text = new StringBuilder();
        if (c.min() != null) {
            text.append(trim(c.min())).append(unit)
                    .append(c.minInclusive() ? " 이상" : " 초과");
        }
        if (c.max() != null) {
            if (!text.isEmpty()) {
                text.append(" ~ ");
            }
            text.append(trim(c.max())).append(unit)
                    .append(c.maxInclusive() ? " 이하" : " 미만");
        }
        return text.isEmpty() ? "조건 명시 없음" : text.toString();
    }

    private boolean below(double value, double min, boolean inclusive) {
        return inclusive ? value < min : value <= min;
    }

    private boolean above(double value, double max, boolean inclusive) {
        return inclusive ? value > max : value >= max;
    }

    private String trim(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value) : String.valueOf(value);
    }

    private String join(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + " " + b;
    }
}
