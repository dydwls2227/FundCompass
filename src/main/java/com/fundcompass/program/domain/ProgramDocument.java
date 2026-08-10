package com.fundcompass.program.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "program_document",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_program_document_url",
                columnNames = {"program_id", "file_url"}),
        indexes = @Index(name = "idx_program_document_status", columnList = "status")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProgramDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private DocumentFileType fileType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExtractionStatus status;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

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

    public void markExtracted(String text) {
        this.extractedText = text;
        this.status = ExtractionStatus.EXTRACTED;
        this.errorMessage = null;
        this.extractedAt = LocalDateTime.now();
    }

    public void markUnsupported() {
        this.status = ExtractionStatus.UNSUPPORTED;
        this.errorMessage = null;
        this.extractedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.status = ExtractionStatus.FAILED;
        this.errorMessage = message == null ? null
                : message.substring(0, Math.min(message.length(), 500));
        this.extractedAt = LocalDateTime.now();
    }
}
