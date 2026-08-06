package com.fundcompass.program.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BizinfoProgramMapperTest {

    @Test
    @DisplayName("정상 포맷의 신청기간을 시작일과 종료일로 분리한다")
    void 신청기간_정상_파싱() {
        var period = BizinfoProgramMapper.parseApplyPeriod("2026-08-03 ~ 2026-08-17");

        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    @Test
    @DisplayName("파싱할 수 없는 신청기간은 예외 없이 빈 값을 반환한다")
    void 신청기간_파싱_실패() {
        assertThat(BizinfoProgramMapper.parseApplyPeriod("상시모집").start()).isNull();
        assertThat(BizinfoProgramMapper.parseApplyPeriod("예산 소진 시까지").end()).isNull();
        assertThat(BizinfoProgramMapper.parseApplyPeriod(null).start()).isNull();
        assertThat(BizinfoProgramMapper.parseApplyPeriod("").start()).isNull();
    }
}