import { api } from "./client";
import { expectOk, unwrap } from "./errors";
import type { NotificationItem } from "./types";

/** 알림함·배지가 같은 목록을 쓴다(D74). */
export const NOTIFICATIONS_KEY = ["notifications"] as const;

export async function fetchNotifications(): Promise<{
  items: NotificationItem[];
  unreadCount: number;
}> {
  const inbox = unwrap(await api.GET("/api/v1/notifications"));
  return { items: inbox.items ?? [], unreadCount: inbox.unreadCount ?? 0 };
}

/** 멱등. */
export async function markNotificationRead(id: number): Promise<void> {
  expectOk(
    await api.POST("/api/v1/notifications/{id}/read", {
      params: { path: { id } },
    }),
  );
}

export async function markAllNotificationsRead(): Promise<void> {
  expectOk(await api.POST("/api/v1/notifications/read-all"));
}
