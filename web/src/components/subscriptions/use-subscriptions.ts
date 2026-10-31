"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchSubscriptions, SUBSCRIPTIONS_KEY } from "@/lib/api/subscriptions";

/** 내 구독 목록 — 로그인일 때만 부른다. */
export function useSubscriptions(enabled: boolean) {
  return useQuery({
    queryKey: SUBSCRIPTIONS_KEY,
    queryFn: fetchSubscriptions,
    enabled,
    staleTime: 5 * 60_000,
  });
}
