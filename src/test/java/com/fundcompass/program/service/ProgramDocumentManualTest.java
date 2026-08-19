package com.fundcompass.program.service;

import com.fundcompass.program.domain.DocumentFileType;
import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.repository.ProgramDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProgramDocumentManualTest {

    @Autowired
    ProgramDocumentService programDocumentService;

    @Autowired
    ProgramDocumentRepository documentRepository;

    @Test
    @DisplayName("공고에서 대표 문서를 등록한다")
    void 문서_등록() {
        var result = programDocumentService.registerDocuments();
        System.out.println("=".repeat(60));
        System.out.println("등록 결과: " + result);
        System.out.println("총 문서: " + documentRepository.count());
        System.out.println("=".repeat(60));
    }

    @Test
    @DisplayName("대기 중인 문서 10건만 추출한다")
    void 추출_10건() {
        var result = programDocumentService.extractPending(10);
        System.out.println("=".repeat(60));
        System.out.println("추출 결과: " + result);
        System.out.println("PENDING 남은 수: "
                + documentRepository.countByStatus(ExtractionStatus.PENDING));
        System.out.println("=".repeat(60));
    }

    @Test
    @DisplayName("대기 중인 문서를 전부 추출한다")
    void 추출_전체() {
        var result = programDocumentService.extractPending(Integer.MAX_VALUE);
        System.out.println("=".repeat(60));
        System.out.println("추출 결과: " + result);
        System.out.println("=".repeat(60));
    }

    /**
     * HWP 파서가 붙기 전 UNSUPPORTED로 확정된 문서를 추출 대기로 되돌린다.
     * 이 테스트만으로는 추출하지 않는다 — 되돌린 뒤 {@code 추출_전체}를 실행한다.
     */
    @Test
    @DisplayName("HWP 미지원 문서를 추출 대기로 되돌린다")
    void HWP_재개() {
        int reopened = programDocumentService.reopenUnsupported(DocumentFileType.HWP);
        System.out.println("=".repeat(60));
        System.out.printf("되돌림 %,d건%n", reopened);
        System.out.printf("PENDING %,d / UNSUPPORTED %,d%n",
                documentRepository.countByStatus(ExtractionStatus.PENDING),
                documentRepository.countByStatus(ExtractionStatus.UNSUPPORTED));
        System.out.println("=".repeat(60));
    }

    /**
     * EXTRACTED인데 텍스트가 빈 문서를 되돌린다. 이 테스트는 추출하지 않는다 —
     * 되돌린 뒤 {@code 추출_전체}를 실행한다.
     */
    @Test
    @DisplayName("빈 텍스트 문서를 추출 대기로 되돌린다")
    void 빈텍스트_재개() {
        int reopened = programDocumentService.reopenEmptyExtracted();
        System.out.println("=".repeat(60));
        System.out.printf("되돌림 %,d건%n", reopened);
        System.out.printf("PENDING %,d / EXTRACTED %,d / UNSUPPORTED %,d%n",
                documentRepository.countByStatus(ExtractionStatus.PENDING),
                documentRepository.countByStatus(ExtractionStatus.EXTRACTED),
                documentRepository.countByStatus(ExtractionStatus.UNSUPPORTED));
        System.out.println("=".repeat(60));
    }

    @Test
    @DisplayName("문서 상태·형식별 현황")
    void 현황() {
        System.out.println("=".repeat(60));
        for (DocumentFileType type : DocumentFileType.values()) {
            for (ExtractionStatus status : ExtractionStatus.values()) {
                int count = documentRepository.findByFileTypeAndStatus(type, status).size();
                if (count > 0) {
                    System.out.printf("  %-6s %-12s %,6d%n", type, status, count);
                }
            }
        }
        System.out.println("=".repeat(60));
    }
}