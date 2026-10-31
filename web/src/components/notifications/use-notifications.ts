"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchNotifications, NOTIFICATIONS_KEY } from "@/lib/api/notifications";

/** 알림함 — 로그인일 때만. 1분 캐시(배지가 화면 이동마다 새로 받지 않게). */
export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: NOTIFICATIONS_KEY,
    queryFn: fetchNotifications,
    enabled,
    staleTime: 60_000,
  });
}
