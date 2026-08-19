"use client";

import type { Profile } from "./types";

/**
 * 프로필은 sessionStorage에만 둔다.
 * 기획서 F13 — "비로그인 데이터는 서버 영구 저장 없이 세션 범위로 한정".
 * 탭을 닫으면 사라지고 서버에도 남지 않는다.
 */
const KEY = "fundcompass.profile";

export function saveProfile(profile: Profile) {
  sessionStorage.setItem(KEY, JSON.stringify(profile));
}

export function loadProfile(): Profile | null {
  if (typeof window === "undefined") return null;
  const raw = sessionStorage.getItem(KEY);
  return raw ? (JSON.parse(raw) as Profile) : null;
}

export function clearProfile() {
  sessionStorage.removeItem(KEY);
}
