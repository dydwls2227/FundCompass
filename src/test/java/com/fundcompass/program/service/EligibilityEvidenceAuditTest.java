package com.fundcompass.program.service;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 저장된 추출 결과 전체를 원문과 대조한다. API 호출 0회.
 * 프롬프트를 다시 조립해 원문을 복원하므로 배치를 다시 돌릴 필요가 없다.
 *
 * <p>판정 로직은 {@link EligibilityVerification}(프로덕션 코드)을 그대로 쓴다.
 * 테스트가 따로 구현하면 배치가 실제로 적용하는 기준과 어긋난다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EligibilityEvidenceAuditTest {

    @Autowired ProgramEligibilityRepository eligibilityRepository;
    @Autowired ProgramRepository programRepository;
    @Autowired ProgramDocumentRepository documentRepository;
    @Autowired EligibilityExtractor extractor;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("저장된 결과 전체의 evidence를 원문과 대조 (API 호출 없음)")
    void evidence_감사() {
        List<ProgramEligibility> rows = eligibilityRepository.findAll().stream()
                .filter(row -> row.getStatus() == ExtractionStatus.EXTRACTED)
                .filter(row -> row.getExtraction() != null)
                .toList();

        Map<EligibilityVerification.Verdict, Integer> tally =
                new EnumMap<>(EligibilityVerification.Verdict.class);
        int totalLines = 0, matchedLines = 0, skippedLines = 0;
        int staleRows = 0;
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

            List<EligibilityVerification.FieldVerdict> verdicts =
                    EligibilityVerification.verify(extraction, source);

            for (EligibilityVerification.FieldVerdict verdict : verdicts) {
                tally.merge(verdict.verdict(), 1, Integer::sum);
                totalLines += verdict.result().checked();
                matchedLines += verdict.result().matched();
                skippedLines += verdict.result().skipped();

                if (verdict.verdict() != EligibilityVerification.Verdict.MATCHED) {
                    problems.add(String.format("[%d] %-14s %-13s %s",
                            row.getProgramId(), verdict.field(), verdict.verdict(),
                            verdict.result().missing().isEmpty()
                                    ? "" : verdict.result().missing().getFirst()));
                }
            }

            // 저장된 unverified_fields가 지금 기준으로 다시 계산한 것과 같은가
            List<String> recomputed =
                    EligibilityVerification.unmatchedFields(extraction, source);
            if (!recomputed.equals(row.unverifiedFieldList())) {
                staleRows++;
                problems.add(String.format("[%d] 저장값 %s != 재계산 %s  <- 검증 기준이 바뀐 뒤 저장된 행",
                        row.getProgramId(), row.unverifiedFieldList(), recomputed));
            }
        }

        int conditions = tally.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("=".repeat(78));
        System.out.printf("추출 성공 %d건 / 판정된 조건 %d개%n", rows.size(), conditions);
        System.out.println("-".repeat(78));
        for (EligibilityVerification.Verdict verdict : EligibilityVerification.Verdict.values()) {
            int count = tally.getOrDefault(verdict, 0);
            System.out.printf("  %-14s %3d  (%.1f%%)   %s%n",
                    verdict, count, pct(count, conditions), note(verdict));
        }
        System.out.printf("%n  줄 단위 일치 %d/%d (%.1f%%)   대조 생략 %d줄%n",
                matchedLines, totalLines, pct(matchedLines, totalLines), skippedLines);
        System.out.printf("  저장값 불일치 %d행%n", staleRows);

        if (!problems.isEmpty()) {
            System.out.println("-".repeat(78));
            problems.forEach(problem -> System.out.println("  " + problem));
        }
        System.out.println("=".repeat(78));
    }

    /**
     * 저장된 행의 {@code unverified_fields}를 지금 기준으로 다시 계산해 반영한다. API 호출 0회.
     *
     * <p>검증 기준을 고치면 이미 쌓인 행은 옛 기준으로 판정된 채 남는다. 재추출은 쿼터 때문에
     * 불가능하지만, 원문은 프롬프트를 다시 조립해 복원할 수 있으므로 검증만 다시 돌릴 수 있다.
     */
    @Test
    @DisplayName("저장된 행의 unverified_fields 재계산·반영 (API 호출 없음)")
    void 재검증_반영() {
        List<ProgramEligibility> rows = eligibilityRepository.findAll().stream()
                .filter(row -> row.getStatus() == ExtractionStatus.EXTRACTED)
                .filter(row -> row.getExtraction() != null)
                .toList();

        int updated = 0;
        for (ProgramEligibility row : rows) {
            Program program = programRepository.findById(row.getProgramId()).orElse(null);
            if (program == null) {
                continue;
            }
            String source = extractor.buildPrompt(program,
                    documentRepository.findByProgramIdAndStatus(
                            row.getProgramId(), ExtractionStatus.EXTRACTED));

            List<String> recomputed = EligibilityVerification.unmatchedFields(
                    objectMapper.readValue(row.getExtraction(), EligibilityExtraction.class),
                    source);

            if (!recomputed.equals(row.unverifiedFieldList())) {
                System.out.printf("[%d] %s -> %s%n",
                        row.getProgramId(), row.unverifiedFieldList(), recomputed);
                // 재검증은 추출이 아니다. attempts를 올리면 재시도 상한을 잘못 소모한다
                eligibilityRepository.save(row.reverified(recomputed));
                updated++;
            }
        }
        System.out.printf("재검증 %d행 중 %d행 갱신%n", rows.size(), updated);
    }

    private String note(EligibilityVerification.Verdict verdict) {
        return switch (verdict) {
            case MATCHED -> "";
            case UNMATCHED -> "<- 환각 의심. F4 판정에서 제외됨";
            case UNVERIFIABLE -> "<- 근거가 너무 짧음 (판정 보류)";
            case MISSING -> "<- 원칙 위반. F4 판정에서 제외됨";
        };
    }

    private double pct(int part, int total) {
        return total == 0 ? 0 : 100.0 * part / total;
    }
}
