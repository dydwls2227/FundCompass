package com.fundcompass.program.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "program_eligibility",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_program_eligibility_program",
                columnNames = "program_id"),
        indexes = @Index(name = "idx_program_eligibility_status", columnList = "status")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProgramEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExtractionStatus status;

    @JdbcTypeCode(SqlTypes.JSON) // Hibernate 6 JSONB
    @Column(columnDefinition = "jsonb")
    private String extraction;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "model_id", nullable = false, length = 50)
    private String modelId;

    @Column(name = "prompt_chars")
    private Integer promptChars;

    @Column(name = "elapsed_ms")
    private Integer elapsedMs;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    public static ProgramEligibility succeeded(Long programId, String extractionJson,
                                               String promptVersion, String modelId,
                                               int promptChars, long elapsedMs) {
        return ProgramEligibility.builder()
                .programId(programId)
                .status(ExtractionStatus.EXTRACTED)
                .extraction(extractionJson)
                .promptVersion(promptVersion)
                .modelId(modelId)
                .promptChars(promptChars)
                .elapsedMs((int) elapsedMs)
                .extractedAt(LocalDateTime.now())
                .build();
    }

    public static ProgramEligibility failed(Long programId, String message,
                                            String promptVersion, String modelId,
                                            int promptChars) {
        return ProgramEligibility.builder()
                .programId(programId)
                .status(ExtractionStatus.FAILED)
                .promptVersion(promptVersion)
                .modelId(modelId)
                .promptChars(promptChars)
                .errorMessage(message == null ? null
                        : message.substring(0, Math.min(message.length(), 500)))
                .extractedAt(LocalDateTime.now())
                .build();
    }
}