package com.fundcompass.program.infra.dto;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fundcompass.program.domain.ConditionStatus;

import java.util.List;

@JsonClassDescription("공고 문서에서 추출한 지원 자격요건")
public record EligibilityExtraction(

        @JsonPropertyDescription("업력 조건. 단위는 년.")
        RangeCondition businessAge,

        @JsonPropertyDescription("연 매출액 조건. 단위는 억원.")
        RangeCondition annualRevenue,

        @JsonPropertyDescription("상시 고용인원 조건. 단위는 명.")
        RangeCondition employeeCount,

        @JsonPropertyDescription("신청 가능한 지역. 예: 서울특별시, 경기도")
        ListCondition regions,

        @JsonPropertyDescription("신청 가능한 업종. 예: 제조업, 음식점업")
        ListCondition industries,

        @JsonPropertyDescription("신청 가능한 사업자 유형. 예: 소상공인, 중소기업, 예비창업자")
        ListCondition businessTypes
) {

    @JsonClassDescription("최솟값/최댓값으로 표현되는 수치 조건")
    public record RangeCondition(
            @JsonPropertyDescription("SPECIFIED=조건 명시, NOT_REQUIRED=제한 없음 명시, UNKNOWN=문서에서 찾지 못함")
            ConditionStatus status,

            @JsonPropertyDescription("허용 최솟값. 하한이 없으면 null")
            Double min,

            @JsonPropertyDescription("min 값 자체가 허용되면 true(이상), 허용되지 않으면 false(초과). min이 null이면 true")
            boolean minInclusive,

            @JsonPropertyDescription("허용 최댓값. 상한이 없으면 null")
            Double max,

            @JsonPropertyDescription("max 값 자체가 허용되면 true(이하·이내), 허용되지 않으면 false(미만). max가 null이면 true")
            boolean maxInclusive,

            @JsonPropertyDescription("판단 근거가 된 문서의 문장을 그대로 인용."
                    + " 답(values·숫자)을 다시 적는 칸이 아니라 그 값을 읽어낸 자리를 가리키는 칸이다."
                    + " 원문에 연속으로 존재하는 구간만 적는다. UNKNOWN이면 null")
            String evidence
    ) {}

    @JsonClassDescription("목록으로 표현되는 조건")
    public record ListCondition(
            @JsonPropertyDescription("SPECIFIED=조건 명시, NOT_REQUIRED=제한 없음 명시, UNKNOWN=문서에서 찾지 못함")
            ConditionStatus status,

            @JsonPropertyDescription("허용되는 값 목록. SPECIFIED가 아니면 빈 배열")
            List<String> values,

            @JsonPropertyDescription("판단 근거가 된 문서의 문장을 그대로 인용."
                    + " 답(values·숫자)을 다시 적는 칸이 아니라 그 값을 읽어낸 자리를 가리키는 칸이다."
                    + " 원문에 연속으로 존재하는 구간만 적는다. UNKNOWN이면 null")
            String evidence
    ) {}
}