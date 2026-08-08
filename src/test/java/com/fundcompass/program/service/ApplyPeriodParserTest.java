package com.fundcompass.program.service;

import com.fundcompass.program.domain.DeadlineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyPeriodParserTest {

    @Test
    @DisplayName("날짜 범위는 시작일과 종료일을 갖는다")
    void 날짜범위_파싱() {
        var period = ApplyPeriodParser.parse("2026-08-03 ~ 2026-08-17");

        assertThat(period.type()).isEqualTo(DeadlineType.FIXED_PERIOD);
        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    @Test
    @DisplayName("점 구분 날짜도 파싱한다")
    void 점구분_날짜() {
        var period = ApplyPeriodParser.parse("2020.01.01 ~ 2026.12.31");

        assertThat(period.type()).isEqualTo(DeadlineType.FIXED_PERIOD);
        assertThat(period.start()).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    @ParameterizedTest
    @CsvSource({
            "예산 소진시까지,       UNTIL_BUDGET",
            "예산 소진시 까지,      UNTIL_BUDGET",
            "예산 소진 시까지,      UNTIL_BUDGET",
            "예산 소진시까 지,      UNTIL_BUDGET",
            "2026년 4월 ~ 예산 소진시까 지, UNTIL_BUDGET",
            "상시 접수,             ALWAYS_OPEN",
            "수시 접수,             ALWAYS_OPEN",
            "수시모집,              ALWAYS_OPEN",
            "연중 상시 모집,        ALWAYS_OPEN",
            "선착순 접수,           FIRST_COME",
            "선착순 마감,           FIRST_COME",
            "선순 마감,             FIRST_COME",
            "모집 완료시,           FIRST_COME",
            "모집 완료 시,          FIRST_COME",
            "모집 마감시,           FIRST_COME",
            "모집규모 충족시,       FIRST_COME",
            "세부사업별 상이,       VARIES",
            "차수별 상이,           VARIES",
            "모집방식별 상이,       VARIES",
            "추후 공지,             UNKNOWN"
    })
    @DisplayName("실측된 서술형 신청기간을 유형으로 분류한다")
    void 서술형_분류(String raw, DeadlineType expected) {
        assertThat(ApplyPeriodParser.parse(raw).type()).isEqualTo(expected);
    }

    @Test
    @DisplayName("빈 값과 null은 UNKNOWN으로 처리하며 예외를 던지지 않는다")
    void 빈값_처리() {
        assertThat(ApplyPeriodParser.parse(null).type()).isEqualTo(DeadlineType.UNKNOWN);
        assertThat(ApplyPeriodParser.parse("").type()).isEqualTo(DeadlineType.UNKNOWN);
        assertThat(ApplyPeriodParser.parse("   ").type()).isEqualTo(DeadlineType.UNKNOWN);
    }
}