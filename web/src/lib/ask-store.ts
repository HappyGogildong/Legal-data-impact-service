import { useSyncExternalStore } from "react";
import type { LawRefInput } from "@/lib/api/types";

/**
 * 지금 탭의 질문 — 화면 URL 이 아니라 sessionStorage(D50·observability §4: 질문에 개인 사정이 섞일 수 있어 접근 로그·브라우저 기록에 남기지 않는다).
 * 분석 API 로는 POST 본문으로 보낸다(lib/api/analysis.ts).
 * 저장소를 못 쓰는 환경(사생활 보호 모드 등)이면 메모리로 대신한다.
 */
export type AskRequest = {
  query: string;
  lawRef?: LawRefInput;
  profileSearch?: boolean;
};

const ASK_KEY = "lia.ask";
const DRAFT_KEY = "lia.draft";

const listeners = new Set<() => void>();
const memory = new Map<string, string>();
const parsed = new Map<
  string,
  { raw: string | null; value: AskRequest | null }
>();

function readRaw(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return memory.get(key) ?? null;
  }
}

function writeRaw(key: string, value: string | null) {
  if (value === null) memory.delete(key);
  else memory.set(key, value);
  try {
    if (value === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, value);
  } catch {
    // 메모리 값으로 계속
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** useSyncExternalStore 는 같은 값이면 같은 참조를 원한다 — 원문 문자열이 같으면 이전 객체를 돌려준다. */
function readAsk(): AskRequest | null {
  const raw = readRaw(ASK_KEY);
  const cached = parsed.get(ASK_KEY);
  if (cached && cached.raw === raw) return cached.value;
  let value: AskRequest | null = null;
  try {
    value = raw ? (JSON.parse(raw) as AskRequest) : null;
  } catch {
    value = null;
  }
  parsed.set(ASK_KEY, { raw, value });
  return value;
}

export function setAsk(request: AskRequest) {
  writeRaw(ASK_KEY, JSON.stringify(request));
}

export function useAsk(): AskRequest | null {
  return useSyncExternalStore(subscribe, readAsk, () => null);
}

/** 분석 캐시 키 — 같은 질문·같은 법령·같은 프로필 기준 찾기 여부면 같은 결과(답변 캐시 D51과 같은 단위). */
export function askKey(request: AskRequest): string {
  return JSON.stringify([
    request.query,
    request.lawRef?.lawId ?? null,
    request.lawRef?.effectiveDate ?? null,
    request.profileSearch ?? false,
  ]);
}

/** "질문 고치기" — 홈 질문창이 받아 채울 문장. */
export function setDraft(query: string) {
  writeRaw(DRAFT_KEY, query);
}

export function clearDraft() {
  writeRaw(DRAFT_KEY, null);
}

export function useDraft(): string {
  return useSyncExternalStore(
    subscribe,
    () => readRaw(DRAFT_KEY) ?? "",
    () => "",
  );
}
