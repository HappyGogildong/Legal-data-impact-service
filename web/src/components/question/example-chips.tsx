"use client";

import { useRouter } from "next/navigation";
import { clearDraft, setAsk } from "@/lib/ask-store";

/** 예시 질문 — 법령 이름이 든 질문(법령 발견 S04 는 3단계). */
const EXAMPLES = [
  "주택법 바뀌면 전세 계약 어떻게 돼?",
  "주택법 개정 내용 쉽게 알려줘",
  "근로기준법 뭐가 바뀌어?",
];

export function ExampleChips() {
  const router = useRouter();
  return (
    <ul aria-label="예시 질문" className="mt-2 flex flex-wrap gap-1.5">
      {EXAMPLES.map((example) => (
        <li key={example}>
          <button
            type="button"
            onClick={() => {
              clearDraft();
              setAsk({ query: example });
              router.push("/ask");
            }}
            className="rounded-full bg-white/15 px-2.5 py-1 text-xs text-white"
          >
            {example}
          </button>
        </li>
      ))}
    </ul>
  );
}
