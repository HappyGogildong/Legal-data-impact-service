import type { ActionRevision } from "@/lib/api/types";

/** 상황 확인 상태 — 탭 sessionStorage(법령 버전별). 서버엔 질문만 있고 답은 여기에만(D71). 탭을 닫으면 사라진다. */
export type SituationState = {
  answers: Record<string, string>;
  revision?: ActionRevision;
};

const PREFIX = "lia.situation.";
const memory = new Map<string, string>();

export function situationKey(lawId: string, effectiveDate: string): string {
  return `${PREFIX}${lawId}@${effectiveDate}`;
}

export function readSituation(key: string): SituationState {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(key);
  } catch {
    raw = memory.get(key) ?? null;
  }
  if (!raw) return { answers: {} };
  try {
    return JSON.parse(raw) as SituationState;
  } catch {
    return { answers: {} };
  }
}

export function writeSituation(key: string, state: SituationState) {
  const raw = JSON.stringify(state);
  memory.set(key, raw);
  try {
    sessionStorage.setItem(key, raw);
  } catch {
    // 메모리 값으로 계속
  }
}
