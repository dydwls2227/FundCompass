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
import java.util.List;

/**
 * 연속 일치에 실패한 인용을 조각 길이로 분해해 본다. API 호출 0회.
 *
 * <p>"표를 건너뛰며 읽은 정확한 독해"와 "글자를 바꿔 적은 각색"이 조각 길이로 갈리는지
 * <b>측정</b>하는 것이 목적이다. 임계값을 감으로 정하면 앞서 두 번 그랬듯 또 틀린다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EvidenceRunLengthProbeTest {

    @Autowired ProgramEligibilityRepository eligibilityRepository;
    @Autowired ProgramRepository programRepository;
    @Autowired ProgramDocumentRepository documentRepository;
    @Autowired EligibilityExtractor extractor;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("연속 일치 실패 인용의 조각 길이 분포 (API 호출 없음)")
    void 조각_길이_측정() {
        List<String> report = new ArrayList<>();

        for (ProgramEligibility row : eligibilityRepository.findAll()) {
            if (row.getStatus() != ExtractionStatus.EXTRACTED || row.getExtraction() == null) {
                continue;
            }
            Program program = programRepository.findById(row.getProgramId()).orElse(null);
            if (program == null) {
                continue;
            }
            String source = extractor.buildPrompt(program,
                    documentRepository.findByProgramIdAndStatus(
                            row.getProgramId(), ExtractionStatus.EXTRACTED));

            EligibilityExtraction extraction =
                    objectMapper.readValue(row.getExtraction(), EligibilityExtraction.class);

            for (EligibilityVerification.FieldVerdict verdict
                    : EligibilityVerification.verify(extraction, source)) {

                if (verdict.verdict() == EligibilityVerification.Verdict.MATCHED) {
                    continue;
                }
                for (String missing : verdict.result().altered()) {
                    List<Integer> runs = EvidenceVerifier.runsOf(missing, source);
                    report.add(String.format("%-4s [%4d] %-14s 조각 %2d개  최소 %2s  길이 %s%n        %s",
                            row.getPromptVersion(), row.getProgramId(), verdict.field(),
                            runs.size(),
                            runs.isEmpty() ? "-" : String.valueOf(runs.stream()
                                    .mapToInt(Integer::intValue).min().orElse(0)),
                            runs,
                            missing.length() > 100 ? missing.substring(0, 100) + "…" : missing));
                }
            }
        }

        System.out.println("=".repeat(88));
        System.out.println("연속 일치 실패 인용의 조각 분해");
        System.out.println("-".repeat(88));
        report.forEach(System.out::println);
        System.out.println("-".repeat(88));
        calibrate();
        System.out.println("=".repeat(88));
    }

    /**
     * 유일하게 확인된 <b>진짜 각색</b>을 같은 자로 재본다 (v4에서 삭제한 1005 행,
     * {@code docs/archive/eligibility-v4-rows-20260816.json}에 보존).
     *
     * <p>원문은 {@code 자원의 절약과 재활용촉진}인데 모델이 {@code 절약 및}로 고쳐 적었다.
     * 양성 표본이 이 하나뿐이라 임계값을 이것만으로 정하면 과적합이다. 대조용이다.
     */
    private void calibrate() {
        Program program = programRepository.findById(1005L).orElse(null);
        if (program == null) {
            System.out.println("교정 표본: programId=1005 없음");
            return;
        }
        String source = extractor.buildPrompt(program,
                documentRepository.findByProgramIdAndStatus(1005L, ExtractionStatus.EXTRACTED));

        String altered = "③「자원의 절약 및 재활용촉진에 관한 법률」 제31조 제1항 제1호";
        String faithful = "③「자원의 절약과 재활용촉진에 관한 법률」 제31조 제1항 제1호";

        System.out.printf("교정  각색본 조각 %s%n", EvidenceVerifier.runsOf(altered, source));
        System.out.printf("교정  원문대로 조각 %s%n", EvidenceVerifier.runsOf(faithful, source));
    }
}
