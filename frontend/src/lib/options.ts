/** 폼 선택지. 백엔드 정규화가 실제로 다루는 값에 맞춘다 */

export const PROVINCES = [
  "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시",
  "대전광역시", "울산광역시", "세종특별자치시", "경기도", "강원특별자치도",
  "충청북도", "충청남도", "전북특별자치도", "전라남도", "경상북도",
  "경상남도", "제주특별자치도",
] as const;

/**
 * 사업자 유형. BusinessTypeTaxonomy 가 계층으로 다루는 값을 우선 노출한다.
 * 판정률이 93.4%로 가장 높은 조건이라 이걸 고르는 것만으로 대부분의 공고가 판정된다.
 */
export const BUSINESS_TYPES = [
  { value: "소상공인", hint: "상시근로자 5인(제조업 10인) 미만" },
  { value: "소공인", hint: "제조업 소상공인" },
  { value: "소기업", hint: "" },
  { value: "중소기업", hint: "" },
  { value: "중견기업", hint: "" },
  { value: "예비창업자", hint: "아직 사업자등록 전" },
  { value: "개인사업자", hint: "" },
  { value: "법인사업자", hint: "" },
  { value: "사회적기업", hint: "" },
  { value: "협동조합", hint: "" },
] as const;

export const REVENUE_BANDS = [
  { value: "UNDER_1", label: "1억원 미만" },
  { value: "UNDER_3", label: "1억 ~ 3억원" },
  { value: "UNDER_10", label: "3억 ~ 10억원" },
  { value: "UNDER_30", label: "10억 ~ 30억원" },
  { value: "UNDER_80", label: "30억 ~ 80억원" },
  { value: "UNDER_120", label: "80억 ~ 120억원" },
  { value: "OVER_120", label: "120억원 이상" },
] as const;
