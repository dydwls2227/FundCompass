package com.fundcompass.program.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 누적 시도 횟수. 상한 미만인 FAILED 행은 다음 배치에서 다시 도전한다 */
    @Column(nullable = false)
    private Integer attempts;

    /** evidence 대조에 실패해 F4가 신뢰하면 안 되는 필드(쉼표 구분). 없으면 null */
    @Column(name = "unverified_fields", length = 200)
    private String unverifiedFields;

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
                                               int promptChars, long elapsedMs,
                                               List<String> unverifiedFields) {
        return ProgramEligibility.builder()
                .programId(programId)
                .status(ExtractionStatus.EXTRACTED)
                .extraction(extractionJson)
                .promptVersion(promptVersion)
                .modelId(modelId)
                .promptChars(promptChars)
                .elapsedMs((int) elapsedMs)
                .attempts(1)
                .unverifiedFields(join(unverifiedFields))
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
                .errorMessage(truncate(message))
                .attempts(1)
                .extractedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 이미 있는 행을 성공으로 갱신한다. 이전 시도가 실패해 남긴 행을 덮어쓰는 경로다.
     * 공고당 1행(유니크 제약)이므로 새로 insert하면 제약 위반이 난다.
     */
    public ProgramEligibility markSucceeded(String extractionJson, String promptVersion,
                                            String modelId, int promptChars, long elapsedMs,
                                            List<String> unverifiedFields) {
        this.status = ExtractionStatus.EXTRACTED;
        this.extraction = extractionJson;
        this.promptVersion = promptVersion;
        this.modelId = modelId;
        this.promptChars = promptChars;
        this.elapsedMs = (int) elapsedMs;
        this.errorMessage = null;              // 성공했으므로 이전 실패 사유는 지운다
        this.unverifiedFields = join(unverifiedFields);
        this.extractedAt = LocalDateTime.now();
        this.attempts = this.attempts + 1;
        return this;
    }

    /** 이미 있는 행을 실패로 갱신하고 시도 횟수를 올린다 */
    public ProgramEligibility markFailed(String message, String promptVersion,
                                         String modelId, int promptChars) {
        this.status = ExtractionStatus.FAILED;
        this.promptVersion = promptVersion;
        this.modelId = modelId;
        this.promptChars = promptChars;
        this.errorMessage = truncate(message);
        this.extractedAt = LocalDateTime.now();
        this.attempts = this.attempts + 1;
        return this;
    }

    private static String truncate(String message) {
        return message == null ? null : message.substring(0, Math.min(message.length(), 500));
    }

    /**
     * 검증 기준이 바뀌었을 때 판정만 다시 반영한다.
     * 추출을 다시 한 것이 아니므로 {@code attempts}와 {@code extractedAt}은 건드리지 않는다.
     */
    public ProgramEligibility reverified(List<String> unverifiedFields) {
        this.unverifiedFields = join(unverifiedFields);
        return this;
    }

    private static String join(List<String> fields) {
        return fields == null || fields.isEmpty() ? null : String.join(",", fields);
    }

    /** F4가 판정에서 제외해야 하는 필드 */
    public List<String> unverifiedFieldList() {
        return unverifiedFields == null || unverifiedFields.isBlank()
                ? List.of() : List.of(unverifiedFields.split(","));
    }
}