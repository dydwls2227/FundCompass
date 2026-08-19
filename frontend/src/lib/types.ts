// 백엔드 DTO 대응. 서버가 내려주는 모양을 그대로 따른다.

export type ConditionVerdict = "SATISFIED" | "VIOLATED" | "UNKNOWN";
export type EligibilityVerdict = "ELIGIBLE" | "INELIGIBLE" | "NEEDS_REVIEW";

/** 판정하지 못한 이유. 사용자에게 요구할 행동이 서로 다르다 */
export type UnknownReason =
  | "NOT_STATED"              // 공고에 조건이 없음 — 할 일 없음
  | "EVIDENCE_UNTRUSTED"      // 근거 신뢰 불가 — 할 일 없음
  | "PROFILE_MISSING"         // 사용자가 값을 안 냄 — 입력하면 해소
  | "NOT_NORMALIZABLE"        // 우리가 해석 못함 — 할 일 없음
  | "BAND_STRADDLES_BOUNDARY"; // 매출 구간이 경계를 걸침 — 입력하면 해소

export type RevenueBand =
  | "UNDER_1" | "UNDER_3" | "UNDER_10" | "UNDER_30"
  | "UNDER_80" | "UNDER_120" | "OVER_120";

export interface ConditionResult {
  field: string;
  label: string;
  verdict: ConditionVerdict;
  unknownReason: UnknownReason | null;
  requirement: string | null;
  actual: string | null;
  evidence: string | null;
  eligibleFrom: string | null;
}

export interface ProgramMatch {
  programId: number;
  name: string;
  institution: string | null;
  detailUrl: string | null;
  applyEndDate: string | null;
  daysLeft: number | null;
  closed: boolean;
  verdict: EligibilityVerdict;
  judgedCount: number;
  conditions: ConditionResult[];
  actionable: ConditionResult[];
  eligibleFrom: string | null;
}

export interface MatchResponse {
  counts: Record<EligibilityVerdict, number>;
  verdict: EligibilityVerdict | null;
  items: ProgramMatch[];
  totalMatched: number;
  excludedClosed: number;
}

export interface ExplanationResponse {
  programId: number;
  explanation: string | null;
  generated: boolean;
  note: string | null;
}

export interface Profile {
  foundedOn?: string | null;
  revenueBand?: RevenueBand | null;
  employeeCount?: number | null;
  province?: string | null;
  city?: string | null;
  industryName?: string | null;
  businessType?: string | null;
}
