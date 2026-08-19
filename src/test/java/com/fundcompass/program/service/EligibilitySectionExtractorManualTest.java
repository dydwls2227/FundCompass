package com.fundcompass.program.service;

import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.ProgramDocument;
import com.fundcompass.program.infra.dto.EligibilityExtraction;
import com.fundcompass.program.repository.ProgramDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootTest
class EligibilitySectionExtractorManualTest {

    // 앵커 키워드가 아니라 조건의 "값". 앵커와 독립이라 소실을 실제로 잡아낸다
    private static final Pattern CONDITION_VALUE = Pattern.compile(
            "\\d+\\s?년\\s?(이내|이상|미만|이하)"
                    + "|\\d+\\s?억\\s?원?\\s?(이내|이상|미만|이하)"
                    + "|\\d+\\s?명\\s?(이내|이상|미만|이하)");

    @Autowired
    ProgramDocumentRepository documentRepository;

    @Autowired
    EligibilitySectionExtractor extractor;

    @Test
    @DisplayName("자르기 통계 100건")
    void 자르기_통계() {
        List<ProgramDocument> documents = targets(0, 100);

        long originalTotal = 0, resultTotal = 0;
        int atLimit = 0, valuesBefore = 0, valuesAfter = 0, lostAll = 0;

        for (ProgramDocument document : documents) {
            String original = document.getExtractedText();
            String result = extractor.extract(original);

            originalTotal += original.length();
            resultTotal += result.length();
            if (result.length() >= 8_000) {
                atLimit++;
            }

            int before = count(original);
            int after = count(result);
            valuesBefore += before;
            valuesAfter += after;
            if (before > 0 && after == 0) {
                lostAll++;
            }
        }

        System.out.println("=".repeat(60));
        System.out.printf("문서 %d건%n", documents.size());
        System.out.printf("원본 평균 %,d자 → 결과 평균 %,d자 (%.1f%%)%n",
                originalTotal / documents.size(), resultTotal / documents.size(),
                100.0 * resultTotal / originalTotal);
        System.out.printf("8,000자 상한 도달 %d건%n", atLimit);
        System.out.printf("정량 표현 보존 %d / %d (%.1f%%)%n",
                valuesAfter, valuesBefore, 100.0 * valuesAfter / valuesBefore);
        System.out.printf("정량 표현 전부 소실 %d건%n", lostAll);
        System.out.println("=".repeat(60));
    }

    @Test
    @DisplayName("자르기 샘플 3건 육안 확인")
    void 자르기_샘플() {
        for (ProgramDocument document : targets(5_000, 3)) {
            String result = extractor.extract(document.getExtractedText());
            System.out.println("=".repeat(80));
            System.out.printf("[%d] %s%n원본 %,d자 → 결과 %,d자%n",
                    document.getId(), document.getFileName(),
                    document.getExtractedText().length(), result.length());
            System.out.println("-".repeat(80));
            System.out.println(result);
        }
    }

    @Test
    @DisplayName("생성되는 JSON 스키마 확인 (API 호출 없음)")
    void 스키마_출력() {
        var converter = new org.springframework.ai.converter.BeanOutputConverter<>(
                EligibilityExtraction.class);
        System.out.println(converter.getJsonSchema());
    }

    private List<ProgramDocument> targets(int minLength, int limit) {
        return documentRepository.findByStatus(ExtractionStatus.EXTRACTED).stream()
                .filter(document -> document.getExtractedText() != null)
                .filter(document -> document.getExtractedText().length() > minLength)
                .limit(limit)
                .toList();
    }

    private int count(String text) {
        Matcher matcher = CONDITION_VALUE.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}