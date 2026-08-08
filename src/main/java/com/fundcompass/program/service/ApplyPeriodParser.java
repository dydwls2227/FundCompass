package com.fundcompass.program.service;

import com.fundcompass.program.domain.DeadlineType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Slf4j
public final class ApplyPeriodParser {

    private ApplyPeriodParser() {
    }

    public record ApplyPeriod(DeadlineType type, LocalDate start, LocalDate end) {

        static ApplyPeriod of(DeadlineType type) {
            return new ApplyPeriod(type, null, null);
        }
    }

    private static final DateTimeFormatter DOT_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d");

    public static ApplyPeriod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ApplyPeriod.of(DeadlineType.UNKNOWN);
        }

        if (raw.contains("~")) {
            String[] parts = raw.split("~", 2);
            LocalDate start = parseDate(parts[0]);
            LocalDate end = parseDate(parts[1]);
            if (start != null && end != null) {
                return new ApplyPeriod(DeadlineType.FIXED_PERIOD, start, end);
            }
        }

        String normalized = raw.replaceAll("\\s", "");

        if (normalized.contains("예산소진")) {
            return ApplyPeriod.of(DeadlineType.UNTIL_BUDGET);
        }
        if (normalized.contains("상시") || normalized.contains("수시") || normalized.contains("연중")) {
            return ApplyPeriod.of(DeadlineType.ALWAYS_OPEN);
        }
        if (normalized.contains("선착순") || normalized.contains("선순")
                || normalized.contains("모집완료") || normalized.contains("모집마감")
                || normalized.contains("충족시")) {
            return ApplyPeriod.of(DeadlineType.FIRST_COME);
        }
        if (normalized.endsWith("상이")) {
            return ApplyPeriod.of(DeadlineType.VARIES);
        }

        log.debug("분류 불가한 신청기간: {}", raw);
        return ApplyPeriod.of(DeadlineType.UNKNOWN);
    }

    private static LocalDate parseDate(String value) {
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (Exception ignored) {
            // ISO가 아니면 점 구분 포맷을 시도한다
        }
        try {
            return LocalDate.parse(trimmed, DOT_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}