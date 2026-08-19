import type {
  EligibilityVerdict, ExplanationResponse, MatchResponse, Profile, ProgramMatch,
} from "./types";

/**
 * 배포에서는 빈 문자열 = 같은 오리진. next.config.ts 의 rewrites 가 백엔드로 넘긴다.
 * 로컬에서 백엔드를 직접 부르려면 NEXT_PUBLIC_API_BASE=http://localhost:8080 을 준다.
 */
const BASE = process.env.NEXT_PUBLIC_API_BASE ?? "";

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`요청이 실패했습니다 (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export function fetchMatches(
  profile: Profile, verdict?: EligibilityVerdict, limit = 20,
): Promise<MatchResponse> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (verdict) params.set("verdict", verdict);
  return post<MatchResponse>(`/api/matches?${params}`, profile);
}

export function fetchProgram(id: number, profile: Profile): Promise<ProgramMatch> {
  return post<ProgramMatch>(`/api/matches/${id}`, profile);
}

/** 이 호출만 LLM을 부른다. 상세 화면에서만 쓴다 */
export function fetchExplanation(id: number, profile: Profile): Promise<ExplanationResponse> {
  return post<ExplanationResponse>(`/api/matches/${id}/explanation`, profile);
}
