"use client";

import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { clearDraft, setAsk, useDraft } from "@/lib/ask-store";
import { cn } from "@/lib/utils";

type QuestionBoxProps = { compact?: boolean };

/** 질문창 — 보내면 탭 저장소에 질문을 두고 /ask 로. "질문 고치기" 초안이 오면 다시 그려 채운다(key). */
export function QuestionBox({ compact = false }: QuestionBoxProps) {
  const draft = useDraft();
  return <QuestionForm key={draft} initial={draft} compact={compact} />;
}

function QuestionForm({
  initial,
  compact,
}: {
  initial: string;
  compact: boolean;
}) {
  const router = useRouter();
  const inputId = useId();
  const [value, setValue] = useState(initial);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = value.trim();
    if (!query) return;
    clearDraft();
    setAsk({ query });
    router.push("/ask");
  }

  return (
    <form
      role="search"
      onSubmit={submit}
      className={cn(
        "flex items-center gap-2 rounded-lg bg-white",
        compact ? "px-3 py-1.5" : "border border-border px-3 py-2.5",
      )}
    >
      <label htmlFor={inputId} className="sr-only">
        질문
      </label>
      <input
        id={inputId}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        maxLength={500}
        placeholder={
          compact
            ? "질문을 입력하세요"
            : "예) 주택법 바뀌면 전세 계약 어떻게 돼?"
        }
        className="min-w-0 flex-1 bg-transparent text-[13.5px] text-ink outline-none placeholder:text-muted-foreground"
      />
      <button
        type="submit"
        aria-label="질문 보내기"
        className="font-bold text-navy"
      >
        →
      </button>
    </form>
  );
}
