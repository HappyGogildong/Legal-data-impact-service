"use client";

import { useId, useState } from "react";
import type { LawHeader } from "@/lib/api/types";
import type { AskRequest } from "@/lib/ask-store";

/** 후속 질문 — 지금 보는 법령 맥락만 lawRef 로 이어 간다(screen-plan §0). 맥락은 ✕로 뺄 수 있다. */
export function FollowUpBar({
  law,
  onAsk,
}: {
  law: LawHeader;
  onAsk: (next: AskRequest) => void;
}) {
  const inputId = useId();
  const [keepContext, setKeepContext] = useState(true);
  const [value, setValue] = useState("");
  const lawRef =
    law.lawId && law.effectiveDate
      ? { lawId: law.lawId, effectiveDate: law.effectiveDate }
      : undefined;

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = value.trim();
    if (!query) return;
    onAsk({ query, lawRef: keepContext ? lawRef : undefined });
    setValue("");
  }

  return (
    <form
      onSubmit={submit}
      className="rounded-lg border border-border bg-card p-3 max-lg:sticky max-lg:bottom-12 max-lg:z-10"
    >
      {keepContext && lawRef && (
        <div className="mb-1.5 inline-flex items-center gap-1.5 rounded-full bg-chip px-2 py-0.5 text-[11.5px] text-navy">
          지금 보는 법령: {law.title ?? law.lawId} ({law.effectiveDate})
          <button
            type="button"
            aria-label="법령 맥락 빼기"
            onClick={() => setKeepContext(false)}
          >
            ✕
          </button>
        </div>
      )}
      <div className="flex gap-2">
        <label htmlFor={inputId} className="sr-only">
          후속 질문
        </label>
        {/* 키보드 포커스는 흰 입력 틀 안쪽 2px navy 링으로 보인다(visual-design §3). */}
        <div className="flex min-w-0 flex-1 rounded-md border border-border bg-white focus-within:ring-2 focus-within:ring-inset focus-within:ring-navy">
          <input
            id={inputId}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            maxLength={500}
            placeholder="이어서 물어보기"
            className="min-w-0 flex-1 rounded-md bg-transparent px-2.5 py-1.5 text-[13.5px] outline-none"
          />
        </div>
        <button
          type="submit"
          className="rounded-md bg-navy px-3 text-[13px] font-bold text-white"
        >
          보내기
        </button>
      </div>
    </form>
  );
}
