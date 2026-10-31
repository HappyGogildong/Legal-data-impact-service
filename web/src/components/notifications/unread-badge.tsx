"use client";

import { useSession } from "@/lib/session";
import { useNotifications } from "./use-notifications";

/** 주 메뉴 "알림" 옆 읽지 않은 수 — 로그인일 때만, 0 이면 그리지 않는다(D74). */
export function UnreadBadge() {
  const session = useSession();
  const signedIn = session.isSuccess && session.data !== null;
  const inbox = useNotifications(signedIn);
  const count = inbox.data?.unreadCount ?? 0;
  if (!signedIn || count === 0) return null;
  return (
    <span
      aria-label={`읽지 않은 알림 ${count}개`}
      className="ml-1 rounded-full bg-removed px-1.5 py-0.5 text-[10px] font-bold leading-none text-white"
    >
      {count > 9 ? "9+" : count}
    </span>
  );
}
