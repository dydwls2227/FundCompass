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

    @Query("SELECT e.programId FROM ProgramEligibility e")
    List<Long> findAllProgramIds();
}