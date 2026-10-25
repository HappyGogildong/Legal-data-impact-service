"use client";

import { useRouter } from "next/navigation";
import { FollowUpBar } from "@/components/analysis/follow-up-bar";
import type { LawHeader, LawRefInput } from "@/lib/api/types";
import { setAsk, type AskRequest } from "@/lib/ask-store";

/** "쉬운 요약 보기" — 이 법령(lawRef)으로 분석 화면(S02)을 연다. 질문은 URL 이 아니라 탭 저장소(frontend-stack §5, D65). */
export function SummaryButton({
  title,
  lawRef,
}: {
  title: string;
  lawRef: LawRefInput;
}) {
  const router = useRouter();
  function openSummary() {
    setAsk({ query: `${title} 쉬운 요약`, lawRef });
    router.push("/ask");
  }
  return (
    <button
      type="button"
      onClick={openSummary}
      className="rounded-md bg-navy px-3 py-1.5 text-[13px] font-bold text-white"
    >
      쉬운 요약 보기
    </button>
  );
}

/** "이 법령에 대해 질문하기" — 법령 맥락을 붙인 질문창(S02 후속 질문과 같은 컴포넌트). */
export function AskAboutLaw({ law }: { law: LawHeader }) {
  const router = useRouter();
  function ask(next: AskRequest) {
    setAsk(next);
    router.push("/ask");
  }
  return <FollowUpBar law={law} onAsk={ask} />;
}
