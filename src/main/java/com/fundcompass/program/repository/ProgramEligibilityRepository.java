package com.fundcompass.program.repository;

import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.ProgramEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProgramEligibilityRepository extends JpaRepository<ProgramEligibility, Long> {

    Optional<ProgramEligibility> findByProgramId(Long programId);

    long countByStatus(ExtractionStatus status);

    /**
     * 더 시도할 필요가 없는 공고. 성공했거나, 실패를 상한까지 반복한 경우다.
     *
     * <p>상태와 무관하게 전부 제외하면 일시적 오류(네트워크·5xx)로 남은 FAILED 행 하나가
     * 그 공고를 영구히 배치에서 빼버린다.
     */
    @Query("""
            SELECT e.programId FROM ProgramEligibility e
            WHERE e.status = :succeeded OR e.attempts >= :maxAttempts
            """)
    List<Long> findSettledProgramIds(ExtractionStatus succeeded, int maxAttempts);
}