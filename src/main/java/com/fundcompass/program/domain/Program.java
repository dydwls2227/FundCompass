package com.fundcompass.program.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "program",
        uniqueConstraints = @UniqueConstraint(name =
        "uk_program_pblanc_id", columnNames = "pblanc_id"),
        indexes = {
                @Index(name="idx_program_apply_end_date",
                columnList = "apply_end_date"),
                @Index(name="idx_program_source_updated_at",
                columnList = "source_updated_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Program {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pblanc_id",nullable = false,length = 50)
    private String pblancId;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "detail_url", length=500)
    private String detailUrl;

    @Column(name="summary_html", columnDefinition = "TEXT")
    private String summaryHtml;

    @Column(name="target_name", length = 500)
    private String targetName;

    @Column(name = "apply_period_raw", length = 200)
    private String applyPeriodRaw;

    // applyPeroidRaw 에서 파싱
    @Column(name = "apply_start_date")
    private LocalDate applyStartDate;
    @Column(name = "apply_end_date")
    private LocalDate applyEndDate;

    @Column(name = "supervising_institution",length = 200)
    private String supervisingInstitution;

    @Column(name = "executing_institution",length = 200)
    private String executingInstitution;

    @Column(name = "field_large",length = 100)
    private String fieldLarge;

    @Column(name = "field_middle", length = 100)
    private String fieldMiddle;

    @Column(length = 1000)
    private String hashtags;

    @Column(name = "apply_method", columnDefinition = "TEXT")
    private String applyMethod;

    @Column(length = 500)
    private String contact;

    @Column(name = "attachment_name",length = 500)
    private String attachmentName;

    // 기업마당 기준 생성일시
    @Column(name = "source_created_at")
    private LocalDateTime sourceCreatedAt;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
