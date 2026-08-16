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
        /** 근거가 원문에 있다 */
        MATCHED,
        /** 근거가 원문에 없다. 신뢰 불가 */
        UNMATCHED,
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
     * 근거가 원문에 없어 F4가 신뢰하면 안 되는 필드 이름.
     *
     * <p>{@link Verdict#UNVERIFIABLE}은 포함하지 않는다. 짧아서 검증을 못 한 것이지
     * 틀렸다는 증거는 없다. 근거 없이 값을 버리는 것도 추측이다.
     */
    public static List<String> unmatchedFields(EligibilityExtraction extraction, String source) {
        return verify(extraction, source).stream()
                .filter(verdict -> verdict.verdict() == Verdict.UNMATCHED
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
        } else {
            verdict = Verdict.UNMATCHED;      // 부분 일치도 신뢰하지 않는다
        }
        target.add(new FieldVerdict(field, status, verdict, result));
    }

    private EligibilityVerification() {
    }
}
