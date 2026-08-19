import type { ReactNode } from "react";

export function Field({
  label, hint, optional = true, children,
}: {
  label: string; hint?: string; optional?: boolean; children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="flex flex-wrap items-baseline gap-x-2">
        <span className="text-sm font-semibold text-ink">{label}</span>
        {optional && <span className="text-xs text-ink-faint">선택</span>}
      </span>
      {hint && <span className="mt-0.5 block text-xs text-ink-faint">{hint}</span>}
      <div className="mt-1.5">{children}</div>
    </label>
  );
}

export const inputClass =
  "w-full rounded-lg border border-border bg-surface px-3 py-2.5 text-ink " +
  "outline-none transition focus:border-brand focus:ring-2 focus:ring-brand/15 " +
  "placeholder:text-ink-faint";
