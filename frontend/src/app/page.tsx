"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Field, inputClass } from "@/components/field";
import { Page } from "@/components/shell";
import { BUSINESS_TYPES, PROVINCES, REVENUE_BANDS } from "@/lib/options";
import { saveProfile } from "@/lib/profile";
import type { Profile, RevenueBand } from "@/lib/types";

export default function Onboarding() {
  const router = useRouter();
  const [form, setForm] = useState<Profile>({});
  const [more, setMore] = useState(false);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    saveProfile(form);
    router.push("/results");
  }

  const set = <K extends keyof Profile>(key: K, value: Profile[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  return (
    <Page>
      <section className="mb-8">
        <h1 className="text-2xl font-bold leading-snug tracking-tight text-ink sm:text-3xl">
          받을 수 있는 지원사업만
          <br />
          골라서 보여드립니다
        </h1>
        <p className="mt-3 text-ink-soft">
          지원사업에 참여하지 않은 소상공인의 <strong className="font-semibold text-ink">76.2%</strong>가
          “그런 사업이 있는지 몰랐다”고 답했습니다. 사업자 정보를 입력하면 공고
          <span className="tnum"> 1,447</span>건을 대신 읽고 판정해 드립니다.
        </p>
      </section>

      <form onSubmit={submit} className="space-y-5 rounded-2xl border border-border bg-surface p-5 sm:p-6">
        <div className="rounded-lg bg-brand-soft px-4 py-3 text-sm text-brand">
          아래 <strong className="font-semibold">두 가지만</strong> 골라도 대부분의 공고를 판정할 수 있습니다.
          더 입력할수록 정확해집니다.
        </div>

        <Field label="사업자 유형" optional={false}>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {BUSINESS_TYPES.map((t) => {
              const on = form.businessType === t.value;
              return (
                <button
                  type="button"
                  key={t.value}
                  onClick={() => set("businessType", on ? null : t.value)}
                  aria-pressed={on}
                  className={`rounded-lg border px-3 py-2.5 text-left text-sm transition ${
                    on
                      ? "border-brand bg-brand text-white"
                      : "border-border bg-surface text-ink hover:border-brand/40"
                  }`}
                >
                  <span className="font-medium">{t.value}</span>
                  {t.hint && (
                    <span className={`mt-0.5 block text-[11px] leading-tight ${on ? "text-white/70" : "text-ink-faint"}`}>
                      {t.hint}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="사업장 시·도" optional={false}>
            <select
              className={inputClass}
              value={form.province ?? ""}
              onChange={(e) => set("province", e.target.value || null)}
            >
              <option value="">선택하세요</option>
              {PROVINCES.map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </Field>
          <Field label="시·군·구" hint="지자체 사업 판정에 쓰입니다">
            <input
              className={inputClass}
              placeholder="예) 포항시"
              value={form.city ?? ""}
              onChange={(e) => set("city", e.target.value || null)}
            />
          </Field>
        </div>

        <button
          type="button"
          onClick={() => setMore((v) => !v)}
          className="text-sm font-medium text-brand underline-offset-4 hover:underline"
        >
          {more ? "추가 정보 접기" : "추가 정보 입력하기 (업력·매출·고용)"}
        </button>

        {more && (
          <div className="grid gap-4 border-t border-border pt-5 sm:grid-cols-2">
            <Field label="창업일" hint="업력 조건과 '언제부터 가능한지' 안내에 쓰입니다">
              <input
                type="date"
                className={inputClass}
                value={form.foundedOn ?? ""}
                onChange={(e) => set("foundedOn", e.target.value || null)}
              />
            </Field>
            <Field label="상시근로자 수">
              <input
                type="number"
                min={0}
                inputMode="numeric"
                className={inputClass}
                placeholder="예) 3"
                value={form.employeeCount ?? ""}
                onChange={(e) =>
                  set("employeeCount", e.target.value === "" ? null : Number(e.target.value))
                }
              />
            </Field>
            <Field label="연매출 구간" hint="정확한 금액은 받지 않습니다">
              <select
                className={inputClass}
                value={form.revenueBand ?? ""}
                onChange={(e) => set("revenueBand", (e.target.value || null) as RevenueBand | null)}
              >
                <option value="">선택하세요</option>
                {REVENUE_BANDS.map((b) => (
                  <option key={b.value} value={b.value}>{b.label}</option>
                ))}
              </select>
            </Field>
            <Field label="업종">
              <input
                className={inputClass}
                placeholder="예) 음식점업"
                value={form.industryName ?? ""}
                onChange={(e) => set("industryName", e.target.value || null)}
              />
            </Field>
          </div>
        )}

        <button
          type="submit"
          className="w-full rounded-xl bg-brand px-4 py-3.5 font-semibold text-white transition hover:opacity-90 active:opacity-80"
        >
          지원사업 찾기
        </button>

        <p className="text-center text-xs leading-relaxed text-ink-faint">
          입력한 정보는 서버에 저장되지 않습니다. 판정에만 쓰이고 브라우저 탭을 닫으면 사라집니다.
        </p>
      </form>
    </Page>
  );
}
