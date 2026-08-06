package com.fundcompass.program.repository;

import com.fundcompass.program.domain.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    Optional<Program> findByPblancId(String pblancId);
}
