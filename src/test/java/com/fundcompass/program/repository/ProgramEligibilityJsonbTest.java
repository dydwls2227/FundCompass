package com.fundcompass.program.repository;

import com.fundcompass.program.domain.ProgramEligibility;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProgramEligibilityJsonbTest {

    private static final Long PROGRAM_ID = 955L;
    private static final String JSON = """
            {"businessAge":{"status":"UNKNOWN","min":null,"max":null},"note":"한글 확인"}""";

    @Autowired ProgramEligibilityRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("JSONB 왕복 저장·조회 (API 호출 없음)")
    void jsonb_왕복() {
        repository.findByProgramId(PROGRAM_ID).ifPresent(repository::delete);
        repository.flush();

        repository.save(ProgramEligibility.succeeded(
                PROGRAM_ID, JSON, "v1", "gemini-2.5-flash", 4415, 12103));
        entityManager.flush();
        entityManager.clear();

        String raw = (String) entityManager
                .createNativeQuery("SELECT extraction::text FROM program_eligibility"
                        + " WHERE program_id = " + PROGRAM_ID)
                .getSingleResult();
        String viaJpa = repository.findByProgramId(PROGRAM_ID).orElseThrow().getExtraction();

        System.out.println("=".repeat(78));
        System.out.println("넣은 값  : " + JSON);
        System.out.println("DB 원본  : " + raw);
        System.out.println("JPA 조회 : " + viaJpa);
        System.out.println("-".repeat(78));
        System.out.println("이중 인코딩됨 : " + raw.trim().startsWith("\""));
        System.out.println("JPA 왕복 일치 : " + JSON.equals(viaJpa));
        System.out.println("=".repeat(78));
    }
}