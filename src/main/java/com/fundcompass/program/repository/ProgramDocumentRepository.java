package com.fundcompass.program.repository;

import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.ProgramDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramDocumentRepository extends JpaRepository<ProgramDocument, Long> {
    Optional<ProgramDocument> findByProgramIdAndFileUrl(Long id, String fileUrl);

    List<ProgramDocument> findByStatus(ExtractionStatus status);

    long countByStatus(ExtractionStatus status);
}
