"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useToday } from "@/hooks/use-browser";
import {
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATIONS_KEY,
} from "@/lib/api/notifications";
import { daysUntil, formatSeoulDateTime } from "@/lib/dates";
import { lawHref } from "@/lib/law-view";
import { useRequireSession } from "@/lib/session";
import { useNotifications } from "./use-notifications";

/** 알림 문구 — 오늘부터 시행일까지 남은 날로(배치가 늦게 돌아도 맞게). */
export function notificationMessage(
  title: string,
  effectiveDate: string,
  today: string,
): string {
  const days = daysUntil(effectiveDate, today);
  if (days > 0) return `${title} 개정이 ${days}일 뒤 시행돼요`;
  if (days === 0) return `${title} 개정이 오늘 시행돼요`;
  return `${title} 개정이 시행됐어요`;
}

/** S11 알림함(D74) — 최근 50개, 읽지 않음 강조, 누르면 읽음 → 그 시행본 S05, 모두 읽음. */
export function NotificationListView() {
  const session = useRequireSession();
  const signedIn = session.isSuccess && session.data !== null;
  const inbox = useNotifications(signedIn);
  const queryClient = useQueryClient();
  const router = useRouter();
  const today = useToday();
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY });
  const readOne = useMutation({
    mutationFn: (id: number) => markNotificationRead(id),
    onSettled: refresh,
  });
  const readAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSettled: refresh,
  });

  const items = inbox.data?.items.filter(
    (item) =>
      item.id !== undefined && item.lawId && item.title && item.effectiveDate,
  );

  function open(id: number, lawId: string, effectiveDate: string) {
    readOne.mutate(id);
    router.push(lawHref(lawId, effectiveDate));
  }

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-3 px-4 py-5">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-bold">알림</h1>
        {(inbox.data?.unreadCount ?? 0) > 0 && (
          <button
            type="button"
            onClick={() => readAll.mutate()}
            disabled={readAll.isPending}
            className="text-[12.5px] font-bold text-navy"
          >
            모두 읽음
          </button>
        )}
      </div>
      {inbox.isError && (
        <p role="alert" className="text-sm text-removed">
          알림을 불러오지 못했어요
        </p>
      )}
      {items && items.length === 0 && (
        <p className="text-sm text-muted-foreground">
          <span>아직 알림이 없어요</span>{" "}
          <Link href="/laws" className="font-bold text-navy">
            곧 시행되는 법령 보기
          </Link>
        </p>
      )}
      {items && items.length > 0 && today && (
        <ul
          aria-label="알림 목록"
          className="divide-y divide-border rounded-lg border border-border bg-card text-sm"
        >
          {items.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => open(item.id!, item.lawId!, item.effectiveDate!)}
                className="flex w-full items-start gap-2 px-3.5 py-3 text-left"
              >
                <span
                  aria-hidden
                  className={`mt-1.5 size-1.5 shrink-0 rounded-full ${item.read ? "bg-transparent" : "bg-navy"}`}
                />
                <span className="min-w-0">
                  <span className={item.read ? "" : "font-bold"}>
                    {notificationMessage(
                      item.title!,
                      item.effectiveDate!,
                      today,
                    )}
                  </span>
                  <span className="mt-0.5 block text-xs text-muted-foreground">
                    <span>구독한 법령</span>
                    {item.createdAt
                      ? ` · ${formatSeoulDateTime(item.createdAt)}`
                      : ""}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
