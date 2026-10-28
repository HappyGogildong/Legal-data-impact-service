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

/** "내 영향·대응 방안 보기"(D69) — 이 법령으로 Layer B 를 요청한다. 로그인·프로필이 없으면 응답의 미충족 안내가 로그인 흐름으로 이끈다. */
export function PersonalizeButton({
  title,
  lawRef,
}: {
  title: string;
  lawRef: LawRefInput;
}) {
  const router = useRouter();
  function openPersonalized() {
    setAsk({ query: personalizedQuery(title), lawRef });
    router.push("/ask");
  }
  return (
    <button
      type="button"
      onClick={openPersonalized}
      className="rounded-md border border-navy px-3 py-1.5 text-[13px] font-bold text-navy"
    >
      내 영향·대응 방안 보기
    </button>
  );
}

/** Layer B 요청 질문 — S02·S05 버튼이 같은 문구를 쓴다(플래너가 IMPACT·ACTION 을 고르게). */
export function personalizedQuery(title: string): string {
  return `${title} 내 영향과 대응 방안`;
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
