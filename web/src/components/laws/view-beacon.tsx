"use client";

import { useEffect } from "react";
import { recordLawView } from "@/lib/api/law-views";

const VIEWED_KEY = "lia.viewed";
const memory = new Set<string>();

/** 이 탭에서 처음 보는 버전이면 표시하고 true. 저장소를 못 쓰면 메모리로 대신한다. */
function markViewed(key: string): boolean {
  try {
    const viewed = new Set<string>(
      JSON.parse(sessionStorage.getItem(VIEWED_KEY) ?? "[]"),
    );
    if (viewed.has(key)) return false;
    viewed.add(key);
    sessionStorage.setItem(VIEWED_KEY, JSON.stringify([...viewed]));
    return true;
  } catch {
    if (memory.has(key)) return false;
    memory.add(key);
    return true;
  }
}

/**
 * S05 조회 신호 — 탭당 법령별 1회(D66). 표시를 먼저 남기고 보내서 StrictMode 의 두 번 실행에도 한 번만 간다.
 * 실패해도 화면에는 아무것도 띄우지 않는다. 그리는 것 없음.
 */
export function ViewBeacon({
  lawId,
  effectiveDate,
}: {
  lawId: string;
  effectiveDate: string;
}) {
  useEffect(() => {
    if (!markViewed(`${lawId}@${effectiveDate}`)) return;
    recordLawView(lawId, effectiveDate).catch(() => {});
  }, [lawId, effectiveDate]);
  return null;
}
