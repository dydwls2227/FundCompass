package com.fundcompass.program.service;

import com.fundcompass.program.domain.ConditionStatus;
import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.Program;
import com.fundcompass.program.domain.ProgramEligibility;
import com.fundcompass.program.infra.dto.EligibilityExtraction;
import com.fundcompass.program.repository.ProgramDocumentRepository;
import com.fundcompass.program.repository.ProgramEligibilityRepository;
import com.fundcompass.program.repository.ProgramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 저장된 추출 결과 전체를 원문과 대조한다. API 호출 0회.
 * 프롬프트를 다시 조립해 원문을 복원하므로 배치를 다시 돌릴 필요가 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EligibilityEvidenceAuditTest {

    @Autowired ProgramEligibilityRepository eligibilityRepository;
    @Autowired ProgramRepository programRepository;
    @Autowired ProgramDocumentRepository documentRepository;
    @Autowired EligibilityExtractor extractor;
    @Autowired ObjectMapper objectMapper;

    private record Condition(String name, ConditionStatus status, String evidence) {}

    @Test
    @DisplayName("저장된 결과 전체의 evidence를 원문과 대조 (API 호출 없음)")
    void evidence_감사() {
        List<ProgramEligibility> rows = eligibilityRepository.findAll().stream()
                .filter(row -> row.getStatus() == ExtractionStatus.EXTRACTED)
                .filter(row -> row.getExtraction() != null)
                .toList();

        int clean = 0, partial = 0, broken = 0, noEvidence = 0;
        int totalLines = 0, matchedLines = 0;
        List<String> problems = new ArrayList<>();

        for (ProgramEligibility row : rows) {
            Program program = programRepository.findById(row.getProgramId()).orElse(null);
            if (program == null) {
                continue;
            }
            String source = extractor.buildPrompt(program,
                    documentRepository.findByProgramIdAndStatus(
                            row.getProgramId(), ExtractionStatus.EXTRACTED));

            EligibilityExtraction extraction =
                    objectMapper.readValue(row.getExtraction(), EligibilityExtraction.class);

            for (Condition condition : conditionsOf(extraction)) {
                if (condition.status() == ConditionStatus.UNKNOWN) {
                    continue;
                }
                EvidenceVerifier.Result result =
                        EvidenceVerifier.verify(condition.evidence(), source);

                if (!result.hasEvidence()) {
                    noEvidence++;
                    problems.add(String.format("[%d] %-6s %-12s 근거 없음",
                            row.getProgramId(), condition.name(), condition.status()));
                    continue;
                }
                totalLines += result.checked();
                matchedLines += result.matched();

                if (result.allMatched()) {
                    clean++;
                } else if (result.matched() > 0) {
                    partial++;
                    problems.add(String.format("[%d] %-6s 부분 불일치 %d/%d — %s",
                            row.getProgramId(), condition.name(),
                            result.matched(), result.checked(), result.missing().getFirst()));
                } else {
                    broken++;
                    problems.add(String.format("[%d] %-6s 전부 불일치 — %s",
                            row.getProgramId(), condition.name(), result.missing().getFirst()));
                }
            }
        }

        int conditions = clean + partial + broken + noEvidence;
        System.out.println("=".repeat(78));
        System.out.printf("추출 성공 %d건 / 판정된 조건 %d개%n", rows.size(), conditions);
        System.out.println("-".repeat(78));
        System.out.printf("  전부 일치   %3d  (%.1f%%)%n", clean, pct(clean, conditions));
        System.out.printf("  부분 일치   %3d  (%.1f%%)%n", partial, pct(partial, conditions));
        System.out.printf("  전부 불일치 %3d  (%.1f%%)   <- 환각 의심%n", broken, pct(broken, conditions));
        System.out.printf("  근거 없음   %3d  (%.1f%%)   <- 원칙 위반%n", noEvidence, pct(noEvidence, conditions));
        System.out.printf("%n  줄 단위 일치 %d/%d (%.1f%%)%n",
                matchedLines, totalLines, pct(matchedLines, totalLines));

        if (!problems.isEmpty()) {
            System.out.println("-".repeat(78));
            problems.forEach(problem -> System.out.println("  " + problem));
        }
        System.out.println("=".repeat(78));
    }

    private double pct(int part, int total) {
        return total == 0 ? 0 : 100.0 * part / total;
    }

    private List<Condition> conditionsOf(EligibilityExtraction extraction) {
        List<Condition> conditions = new ArrayList<>();
        add(conditions, "업력", extraction.businessAge());
        add(conditions, "매출", extraction.annualRevenue());
        add(conditions, "고용", extraction.employeeCount());
        add(conditions, "지역", extraction.regions());
        add(conditions, "업종", extraction.industries());
        add(conditions, "유형", extraction.businessTypes());
        return conditions;
    }

    private void add(List<Condition> target, String name,
                     EligibilityExtraction.RangeCondition condition) {
        if (condition != null) {
            target.add(new Condition(name, condition.status(), condition.evidence()));
        }
    }

    private void add(List<Condition> target, String name,
                     EligibilityExtraction.ListCondition condition) {
        if (condition != null) {
            target.add(new Condition(name, condition.status(), condition.evidence()));
        }
    }
}
