import type { ActionRevision } from "@/lib/api/types";

/**
 * 상황 확인 상태 — 탭 sessionStorage(법령 버전별). 서버엔 질문만 있고 답은 여기에만(D71). 탭을 닫으면 사라진다.
 * identity 는 이 상태가 만들어진 질문 묶음의 식별자 — 다시 받아 질문이 바뀌면 옛 답·재생성 결과를 쓰지 않는다.
 */
export type SituationState = {
  answers: Record<string, string>;
  revision?: ActionRevision;
  identity?: string;
};

const PREFIX = "lia.situation.";
const memory = new Map<string, string>();

export function situationKey(lawId: string, effectiveDate: string): string {
  return `${PREFIX}${lawId}@${effectiveDate}`;
}

/** 질문 묶음의 식별자 — 질문 id 와 문구를 이어 붙인 문자열(같은 묶음이면 같다). */
export function questionsIdentity(
  questions: { id?: string; text?: string }[],
): string {
  return questions.map((q) => `${q.id ?? ""}:${q.text ?? ""}`).join("\n");
}

const EMPTY = (): SituationState => ({ answers: {} });

function isState(value: unknown): value is SituationState {
  if (typeof value !== "object" || value === null || Array.isArray(value))
    return false;
  const { answers, revision, identity } = value as Record<string, unknown>;
  if (typeof answers !== "object" || answers === null || Array.isArray(answers))
    return false;
  if (!Object.values(answers).every((answer) => typeof answer === "string"))
    return false;
  if (
    revision !== undefined &&
    (typeof revision !== "object" ||
      revision === null ||
      Array.isArray(revision))
  )
    return false;
  return identity === undefined || typeof identity === "string";
}

/** identity 를 주면 그 질문 묶음으로 만든 상태만 돌려준다 — 다르면 빈 상태. */
export function readSituation(key: string, identity?: string): SituationState {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(key);
  } catch {
    raw = null;
  }
  raw ??= memory.get(key) ?? null;
  if (!raw) return EMPTY();
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return EMPTY();
  }
  if (!isState(parsed)) return EMPTY();
  if (identity !== undefined && parsed.identity !== identity) return EMPTY();
  return parsed;
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

/** 이 법령 버전의 저장된 답·재생성 결과를 지운다(다시 받기). */
export function clearSituation(key: string) {
  memory.delete(key);
  try {
    sessionStorage.removeItem(key);
  } catch {
    // 메모리는 이미 비웠다
  }
}

/** 테스트용 — 메모리 보관분을 비운다. */
export function clearSituationMemory() {
  memory.clear();
}
