"use client";

import { useEffect, useState } from "react";

const STEPS = ["법령 확인 중", "분석 중 — 근거 조문을 읽고 있어요"];

/** 분석 대기 — 수 초~수십 초. 서버 진행 신호가 없어 시간으로 단계를 넘긴다(screen-plan §1 공통 요소). */
export function AnalysisProgress() {
  const [step, setStep] = useState(0);
  useEffect(() => {
    const timer = setTimeout(() => setStep(1), 2500);
    return () => clearTimeout(timer);
  }, []);
  return (
    <div
      role="status"
      aria-live="polite"
      className="mx-auto max-w-[640px] px-4 py-12 text-center"
    >
      <div
        aria-hidden
        className="mx-auto mb-3 size-8 animate-spin rounded-full border-2 border-chip border-t-navy"
      />
      <p className="font-bold">{STEPS[step]}</p>
      <p className="text-xs text-muted-foreground">
        길게는 수십 초 걸릴 수 있어요.
      </p>
    </div>
  );
}
