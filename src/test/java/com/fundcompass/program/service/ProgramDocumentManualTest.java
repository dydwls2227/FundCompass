package com.fundcompass.program.service;

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
}