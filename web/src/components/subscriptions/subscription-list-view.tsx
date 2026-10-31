"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import { DDayBadge } from "@/components/laws/d-day-badge";
import { useToday } from "@/hooks/use-browser";
import { ApiError, isUnauthorized } from "@/lib/api/errors";
import {
  subscribe,
  SUBSCRIPTIONS_KEY,
  unsubscribe,
} from "@/lib/api/subscriptions";
import type { SubscriptionItem } from "@/lib/api/types";
import { lawHref } from "@/lib/law-view";
import { NOTIFY_OPTIONS } from "@/lib/notify-timing";
import { useRequireSession } from "@/lib/session";
import { useSubscriptions } from "./use-subscriptions";

type ShownSubscription = SubscriptionItem & {
  lawId: string;
  title: string;
  notifyBefore: number;
};

function isShown(item: SubscriptionItem): item is ShownSubscription {
  return !!item.lawId && !!item.title && item.notifyBefore !== undefined;
}

/** S12 구독 관리(D74) — 법령 단위, 다음 시행일 가까운 순. 시점은 바꾸면 바로 저장, 끄기는 확인 없이. */
export function SubscriptionListView() {
  const session = useRequireSession();
  const signedIn = session.isSuccess && session.data !== null;
  const subscriptions = useSubscriptions(signedIn);
  const queryClient = useQueryClient();
  const today = useToday();
  const [message, setMessage] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);

  function fail(error: unknown) {
    if (isUnauthorized(error)) setExpired(true);
    else
      setMessage(
        error instanceof ApiError && error.status === 409
          ? error.message
          : "알림 설정을 바꾸지 못했어요",
      );
  }
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: SUBSCRIPTIONS_KEY });
  const change = useMutation({
    mutationFn: ({
      lawId,
      notifyBefore,
    }: {
      lawId: string;
      notifyBefore: number;
    }) => subscribe(lawId, notifyBefore),
    // 낙관적 갱신 — select 가 서버 값으로 제어되므로, 저장·재조회가 끝나기 전에도
    // 고른 시점이 바로 보이게 캐시를 먼저 바꾼다. 실패하면 이전 값으로 되돌린다.
    onMutate: async ({ lawId, notifyBefore }) => {
      setMessage(null);
      await queryClient.cancelQueries({ queryKey: SUBSCRIPTIONS_KEY });
      const previous =
        queryClient.getQueryData<SubscriptionItem[]>(SUBSCRIPTIONS_KEY);
      queryClient.setQueryData<SubscriptionItem[]>(SUBSCRIPTIONS_KEY, (old) =>
        old?.map((item) =>
          item.lawId === lawId ? { ...item, notifyBefore } : item,
        ),
      );
      return { previous };
    },
    onError: (error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(SUBSCRIPTIONS_KEY, context.previous);
      }
      fail(error);
    },
    onSettled: refresh,
  });
  const remove = useMutation({
    mutationFn: (lawId: string) => unsubscribe(lawId),
    onMutate: () => setMessage(null),
    onError: fail,
    onSettled: refresh,
  });

  const items = subscriptions.data?.filter(isShown);

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-3 px-4 py-5">
      <h1 className="text-lg font-bold">구독 관리</h1>
      {message && (
        <p role="alert" className="text-sm text-removed">
          {message}
        </p>
      )}
      {subscriptions.isError && (
        <p role="alert" className="text-sm text-removed">
          구독 목록을 불러오지 못했어요
        </p>
      )}
      {items && items.length === 0 && (
        <p className="text-sm text-muted-foreground">
          법령 화면에서 &apos;시행 알림 받기&apos;를 누르면 여기에 모여요.
        </p>
      )}
      {items && items.length > 0 && (
        <ul
          aria-label="구독한 법령"
          className="divide-y divide-border rounded-lg border border-border bg-card px-3.5 text-sm"
        >
          {items.map((item) => (
            <li
              key={item.lawId}
              className="flex flex-wrap items-center justify-between gap-2 py-3"
            >
              <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                {item.nextEffectiveDate ? (
                  <Link
                    href={lawHref(item.lawId, item.nextEffectiveDate)}
                    className="font-bold"
                  >
                    {item.title}
                  </Link>
                ) : (
                  <span className="font-bold">{item.title}</span>
                )}
                {item.nextEffectiveDate ? (
                  <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                    {today && (
                      <DDayBadge
                        effectiveDate={item.nextEffectiveDate}
                        today={today}
                      />
                    )}{" "}
                    시행예정
                  </span>
                ) : (
                  <span className="text-xs text-muted-foreground">
                    다음 개정을 기다리는 중
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <select
                  aria-label={`${item.title} 알림 시점`}
                  value={item.notifyBefore}
                  onChange={(event) =>
                    change.mutate({
                      lawId: item.lawId,
                      notifyBefore: Number(event.target.value),
                    })
                  }
                  className="rounded-md border border-border bg-white px-2 py-1 text-xs"
                >
                  {NOTIFY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => remove.mutate(item.lawId)}
                  disabled={remove.isPending}
                  aria-label={`${item.title} 알림 끄기`}
                  className="rounded-md border border-border bg-white px-2 py-1 text-xs"
                >
                  끄기
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}
