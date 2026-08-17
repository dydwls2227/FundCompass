package com.fundcompass.program.repository;

import com.fundcompass.program.domain.DocumentFileType;
import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.ProgramDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramDocumentRepository extends JpaRepository<ProgramDocument, Long> {
    Optional<ProgramDocument> findByProgramIdAndFileUrl(Long id, String fileUrl);

    List<ProgramDocument> findByStatus(ExtractionStatus status);

    List<ProgramDocument> findByProgramIdAndStatus(Long programId, ExtractionStatus status);

    List<ProgramDocument> findByFileTypeAndStatus(DocumentFileType fileType,
                                                  ExtractionStatus status);

    long countByStatus(ExtractionStatus status);
}
