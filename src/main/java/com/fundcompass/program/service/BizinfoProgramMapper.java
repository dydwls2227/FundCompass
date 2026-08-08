package com.fundcompass.program.service;

import com.fundcompass.program.domain.Program;
import com.fundcompass.program.infra.dto.BizinfoProgramItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class BizinfoProgramMapper {

    private static final DateTimeFormatter SOURCE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Program toEntity(BizinfoProgramItem item) {
        ApplyPeriodParser.ApplyPeriod period = ApplyPeriodParser.parse(item.reqstBeginEndDe());

        return Program.builder()
                .pblancId(item.pblancId())
                .name(truncate(item.pblancNm(), 500))
                .detailUrl(truncate(item.pblancUrl(), 500))
                .summaryHtml(item.bsnsSumryCn())
                .targetName(truncate(item.trgetNm(), 500))
                .applyPeriodRaw(truncate(item.reqstBeginEndDe(), 200))
                .deadlineType(period.type())
                .applyStartDate(period.start())
                .applyEndDate(period.end())
                .supervisingInstitution(truncate(item.jrsdInsttNm(), 200))
                .executingInstitution(truncate(item.excInsttNm(), 200))
                .fieldLarge(truncate(item.pldirSportRealmLclasCodeNm(), 100))
                .fieldMiddle(truncate(item.pldirSportRealmMlsfcCodeNm(), 100))
                .hashtags(truncate(item.hashtags(), 1000))
                .applyMethod(item.reqstMthPapersCn())
                .contact(truncate(item.refrncNm(), 500))
                .attachmentName(truncate(item.fileNm(), 500))
                .sourceCreatedAt(parseDateTime(item.creatPnttm()))
                .sourceUpdatedAt(parseDateTime(item.updtPnttm()))
                .build();
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), SOURCE_DATE_TIME);
        } catch (Exception e) {
            log.debug("일시 파싱 실패: {}", value);
            return null;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("길이 초과로 잘림({}자 → {}자): {}", value.length(), maxLength,
                value.substring(0, 50));
        return value.substring(0, maxLength);
    }
}