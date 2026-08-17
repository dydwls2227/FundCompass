package com.fundcompass.program.service;

import com.fundcompass.program.domain.ConditionStatus;
import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.Program;
import com.fundcompass.program.domain.ProgramDocument;
import com.fundcompass.program.infra.dto.EligibilityExtraction;
import com.fundcompass.program.repository.ProgramDocumentRepository;
import com.fundcompass.program.repository.ProgramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EligibilityExtractorManualTest {

    @Autowired ProgramRepository programRepository;
    @Autowired ProgramDocumentRepository documentRepository;
    @Autowired EligibilityExtractor extractor;

    @Test
    @DisplayName("생성되는 JSON 스키마 확인 (API 호출 없음)")
    void 스키마_출력() {
        String schema = new BeanOutputConverter<>(EligibilityExtraction.class).getJsonSchema();

        System.out.println("=".repeat(80));
        System.out.println(schema);
        System.out.println("=".repeat(80));
        System.out.printf("스키마 %,d자%n", schema.length());
        System.out.printf("additionalProperties 포함: %s  (Gemini 미지원 키워드)%n",
                schema.contains("additionalProperties"));
        System.out.printf("$ref/$defs 포함: %s  (중첩 record를 참조로 뽑았는지)%n",
                schema.contains("$ref") || schema.contains("$defs"));
        System.out.println("=".repeat(80));
    }

    @Test
    @DisplayName("프롬프트 조립만 확인 (API 호출 없음)")
    void 프롬프트_조립() {
        Program program = pickProgram(2);
        List<ProgramDocument> documents = documentsOf(program);
        String prompt = extractor.buildPrompt(program, documents);

        System.out.println("=".repeat(80));
        System.out.printf("programId=%d  문서 %d개  프롬프트 %,d자%n",
                program.getId(), documents.size(), prompt.length());
        System.out.println("=".repeat(80));
        System.out.println(prompt);
    }

    @Test
    @DisplayName("실제 Gemini 호출 1건")
    void 추출_1건() {
        Program program = programRepository.findById(955L).orElseThrow();
        List<ProgramDocument> documents = documentsOf(program);
        String prompt = extractor.buildPrompt(program, documents);

        long started = System.currentTimeMillis();
        EligibilityExtraction result = extractor.extract(program, documents);
        long elapsed = System.currentTimeMillis() - started;

        System.out.println("=".repeat(80));
        System.out.printf("%s%n[%d] 프롬프트 %,d자 / %,dms%n",
                program.getName(), program.getId(), prompt.length(), elapsed);
        System.out.println("-".repeat(80));

        range("업력(년)", result.businessAge(), prompt);
        range("매출(억원)", result.annualRevenue(), prompt);
        range("고용(명)", result.employeeCount(), prompt);
        list("지역", result.regions(), prompt);
        list("업종", result.industries(), prompt);
        list("사업자유형", result.businessTypes(), prompt);
        System.out.println("=".repeat(80));
    }

    private void range(String label, EligibilityExtraction.RangeCondition condition, String source) {
        if (condition == null) {
            System.out.printf("  %-12s (null 반환)%n", label);
            return;
        }
        System.out.printf("  %-12s %-13s min=%s%s max=%s%s%n",
                label, condition.status(),
                condition.min(),
                condition.min() == null ? "" : (condition.minInclusive() ? "(이상)" : "(초과)"),
                condition.max(),
                condition.max() == null ? "" : (condition.maxInclusive() ? "(이하)" : "(미만)"));
        printEvidence(condition.status(), condition.evidence(), source);
    }

    private void list(String label, EligibilityExtraction.ListCondition condition, String source) {
        if (condition == null) {
            System.out.printf("  %-12s (null 반환)%n", label);
            return;
        }
        System.out.printf("  %-12s %-13s %s%n", label, condition.status(), condition.values());
        printEvidence(condition.status(), condition.evidence(), source);
    }

    // evidence가 원문에 실제로 있는지 = 환각 검출
    private void printEvidence(ConditionStatus status, String evidence, String source) {
        if (status == ConditionStatus.UNKNOWN) {
            return;
        }
        EvidenceVerifier.Result result = EvidenceVerifier.verify(evidence, source);
        if (!result.hasEvidence()) {
            System.out.println("               🚨 근거 없음");
            return;
        }
        System.out.printf("               %s 연속 %d/%d줄  재배열 %d줄  %s%n",
                result.allMatched() ? "✅" : result.faithful() ? "↔" : "🚨",
                result.matched(), result.checked(), result.reordered(),
                evidence.replace("\n", " ⏎ "));
        result.altered().forEach(line ->
                System.out.printf("                  ↳ 각색됨: %s%n", line));
    }

    private Program pickProgram(int minDocuments) {
        Map<Long, Long> countByProgram = documentRepository.findByStatus(ExtractionStatus.EXTRACTED)
                .stream()
                .filter(document -> document.getExtractedText() != null
                        && !document.getExtractedText().isBlank())
                .collect(Collectors.groupingBy(ProgramDocument::getProgramId, Collectors.counting()));

        Long programId = countByProgram.entrySet().stream()
                .filter(entry -> entry.getValue() >= minDocuments)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "추출된 문서가 " + minDocuments + "개 이상인 공고가 없다"));

        return programRepository.findById(programId).orElseThrow();
    }

    private List<ProgramDocument> documentsOf(Program program) {
        return documentRepository.findByProgramIdAndStatus(
                program.getId(), ExtractionStatus.EXTRACTED);
    }
}
