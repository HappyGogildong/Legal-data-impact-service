import { api } from "./client";
import { expectOk, unwrap } from "./errors";
import type { SubscriptionItem } from "./types";

/** 구독 목록 쿼리 키 — 구독 버튼(S02·S05)과 S12 가 같은 목록을 쓴다(D74). */
export const SUBSCRIPTIONS_KEY = ["subscriptions"] as const;

/** 다음 시행일 가까운 순. */
export async function fetchSubscriptions(): Promise<SubscriptionItem[]> {
  return unwrap(await api.GET("/api/v1/subscriptions")).items ?? [];
}

/** 만들거나 시점 변경. 한도 초과면 409 ApiError(메시지를 화면에 그대로). */
export async function subscribe(
  lawId: string,
  notifyBefore: number,
): Promise<void> {
  expectOk(
    await api.PUT("/api/v1/subscriptions/{lawId}", {
      params: { path: { lawId } },
      body: { notifyBefore },
    }),
  );
}

/** 멱등 해지. */
export async function unsubscribe(lawId: string): Promise<void> {
  expectOk(
    await api.DELETE("/api/v1/subscriptions/{lawId}", {
      params: { path: { lawId } },
    }),
  );
}
