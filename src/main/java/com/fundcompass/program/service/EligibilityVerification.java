package com.fundcompass.program.service;

import com.fundcompass.program.domain.ConditionStatus;
import com.fundcompass.program.infra.dto.EligibilityExtraction;

import java.util.ArrayList;
import java.util.List;

/**
 * 추출 결과의 조건 하나하나가 원문에 근거를 두고 있는지 확인한다.
 *
 * <p>어떤 문서가 오추출을 낼지 미리 맞히는 것(통합공고 블록리스트 등)은 표본이 적을 때
 * 과적합하기 쉽다. 대신 결과를 원문과 대조해 <b>사후에 걸러내면</b> 문서 종류와 무관하게 작동한다.
 *
 * <p>근거가 원문에 없는 조건은 "모델이 그렇게 말했다"는 것 외에 아무 것도 뒷받침하지 못한다.
 * F4가 그 값으로 부적격 판정을 내리면 자격이 되는 사업자에게 안 된다고 안내하게 된다.
 * 그런 필드는 판정에서 빼고 <b>확인필요</b>로 흘려보내는 것이 이 프로젝트의 원칙에 맞다.
 */
public final class EligibilityVerification {

    public enum Verdict {
        /** 근거가 원문에 연속으로 존재한다 */
        MATCHED,
        /**
         * 연속은 아니지만 조각 전부가 원문에 있다. PDF 표가 열이 뒤엉켜 추출되는 탓이며,
         * 모델이 표를 <b>올바르게</b> 읽은 경우다. 신뢰하되 F7 검수 대상으로 표시한다.
         */
        REORDERED,
        /** 조각이 짧게 끊긴다. 글자를 바꿔 적었다는 뜻이므로 신뢰 불가 */
        ALTERED,
        /** 근거가 너무 짧아 대조가 무의미하다 (예: "중소기업"). 판정 보류 */
        UNVERIFIABLE,
        /** SPECIFIED/NOT_REQUIRED인데 근거를 대지 않았다. 프롬프트 원칙 위반 */
        MISSING
    }

    public record FieldVerdict(String field, ConditionStatus status,
                               Verdict verdict, EvidenceVerifier.Result result) {}

    /**
     * UNKNOWN이 아닌 조건만 검증한다. UNKNOWN은 애초에 판정하지 않았으므로 대조할 대상이 없다.
     */
    public static List<FieldVerdict> verify(EligibilityExtraction extraction, String source) {
        List<FieldVerdict> verdicts = new ArrayList<>();
        if (extraction == null) {
            return verdicts;
        }
        add(verdicts, "businessAge", extraction.businessAge(), source);
        add(verdicts, "annualRevenue", extraction.annualRevenue(), source);
        add(verdicts, "employeeCount", extraction.employeeCount(), source);
        add(verdicts, "regions", extraction.regions(), source);
        add(verdicts, "industries", extraction.industries(), source);
        add(verdicts, "businessTypes", extraction.businessTypes(), source);
        return verdicts;
    }

    /**
     * 근거를 신뢰할 수 없어 F4가 판정에서 빼야 하는 필드 이름.
     *
     * <p>제외하는 것은 {@link Verdict#ALTERED}(글자를 바꿔 적음)와
     * {@link Verdict#MISSING}(근거 없음) 둘뿐이다.
     *
     * <p>{@link Verdict#REORDERED}는 제외하지 않는다. 조각 전부가 원문에 있고, PDF 표가
     * 뒤엉켜 추출되는 것은 우리 파서의 한계이지 모델의 잘못이 아니다. 실측에서 이것을
     * 환각으로 몰아 정확한 조건 4건을 버리고 있었다.
     *
     * <p>{@link Verdict#UNVERIFIABLE}도 제외하지 않는다. 짧아서 검증을 못 한 것이지
     * 틀렸다는 증거는 없다. 근거 없이 값을 버리는 것도 추측이다.
     */
    public static List<String> unmatchedFields(EligibilityExtraction extraction, String source) {
        return verify(extraction, source).stream()
                .filter(verdict -> verdict.verdict() == Verdict.ALTERED
                        || verdict.verdict() == Verdict.MISSING)
                .map(FieldVerdict::field)
                .toList();
    }

    private static void add(List<FieldVerdict> target, String field,
                            EligibilityExtraction.RangeCondition condition, String source) {
        if (condition != null) {
            add(target, field, condition.status(), condition.evidence(), source);
        }
    }

    private static void add(List<FieldVerdict> target, String field,
                            EligibilityExtraction.ListCondition condition, String source) {
        if (condition != null) {
            add(target, field, condition.status(), condition.evidence(), source);
        }
    }

    private static void add(List<FieldVerdict> target, String field, ConditionStatus status,
                            String evidence, String source) {
        if (status == null || status == ConditionStatus.UNKNOWN) {
            return;
        }
        EvidenceVerifier.Result result = EvidenceVerifier.verify(evidence, source);

        Verdict verdict;
        if (!result.hasEvidence()) {
            verdict = Verdict.MISSING;
        } else if (result.unverifiable()) {
            verdict = Verdict.UNVERIFIABLE;
        } else if (result.allMatched()) {
            verdict = Verdict.MATCHED;
        } else if (result.faithful()) {
            verdict = Verdict.REORDERED;      // 조각은 전부 원문에 있다
        } else {
            verdict = Verdict.ALTERED;        // 한 줄이라도 각색됐으면 그 조건은 신뢰하지 않는다
        }
        target.add(new FieldVerdict(field, status, verdict, result));
    }

    private EligibilityVerification() {
    }
}
