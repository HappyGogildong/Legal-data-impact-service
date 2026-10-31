"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ApiError, isUnauthorized } from "@/lib/api/errors";
import {
  subscribe,
  SUBSCRIPTIONS_KEY,
  unsubscribe,
} from "@/lib/api/subscriptions";
import {
  DEFAULT_NOTIFY_BEFORE,
  NOTIFY_OPTIONS,
  timingLabel,
} from "@/lib/notify-timing";
import { setReturnTo } from "@/lib/return-to";
import { useSession } from "@/lib/session";
import { useSubscriptions } from "./use-subscriptions";

const FAILED = "알림 설정을 바꾸지 못했어요";

/**
 * "시행 알림 받기"(D74, 법령 단위 구독) — S02·S05. 시트에서 알림 시점(당일·1·7·30일 전)을 고른다.
 * 상태는 구독 목록 하나로 판단. 익명이면 돌아올 경로를 두고 로그인으로(돌아와도 자동 구독 없음).
 */
export function SubscribeButton({ lawId }: { lawId: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const session = useSession();
  const signedIn = session.isSuccess && session.data !== null;
  const subscriptions = useSubscriptions(signedIn);
  const current = subscriptions.data?.find((item) => item.lawId === lawId);
  const [open, setOpen] = useState(false);
  const [choice, setChoice] = useState(DEFAULT_NOTIFY_BEFORE);
  const [message, setMessage] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);

  const change = useMutation({
    mutationFn: (next: number | null) =>
      next === null ? unsubscribe(lawId) : subscribe(lawId, next),
    onMutate: () => setMessage(null),
    onSuccess: () => setOpen(false),
    onError: (error) => {
      if (isUnauthorized(error)) setExpired(true);
      else
        setMessage(
          error instanceof ApiError && error.status === 409
            ? error.message
            : FAILED,
        );
    },
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: SUBSCRIPTIONS_KEY }),
  });

  function openSheet() {
    if (!signedIn) {
      setReturnTo(pathname);
      router.push("/login");
      return;
    }
    setChoice(current?.notifyBefore ?? DEFAULT_NOTIFY_BEFORE);
    setMessage(null);
    setOpen(true);
  }

  const label = current
    ? `알림 받는 중 · ${timingLabel(current.notifyBefore ?? DEFAULT_NOTIFY_BEFORE)}`
    : "시행 알림 받기";

  return (
    <>
      <button
        type="button"
        onClick={openSheet}
        disabled={session.isPending || (signedIn && subscriptions.isPending)}
        className="self-start rounded-md border border-border bg-white px-3 py-1.5 text-[13px] font-bold disabled:opacity-50"
      >
        {label}
      </button>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="bottom" className="rounded-t-xl">
          <SheetHeader>
            <SheetTitle>시행 알림</SheetTitle>
            <SheetDescription>
              이 법령의 시행예정 개정마다 알림함으로 알려 드려요.
            </SheetDescription>
          </SheetHeader>
          <div className="flex flex-col gap-3 px-4 pb-6">
            <fieldset className="flex flex-wrap gap-2">
              <legend className="sr-only">알림 시점</legend>
              {NOTIFY_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className="flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-sm"
                >
                  <input
                    type="radio"
                    name="notify-before"
                    value={option.value}
                    checked={choice === option.value}
                    onChange={() => setChoice(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </fieldset>
            {message && (
              <p role="alert" className="text-xs text-removed">
                {message}
              </p>
            )}
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => change.mutate(choice)}
                disabled={change.isPending}
                className="rounded-md bg-navy px-3 py-1.5 text-[13px] font-bold text-white disabled:opacity-50"
              >
                {current ? "저장" : "알림 받기"}
              </button>
              {current && (
                <button
                  type="button"
                  onClick={() => change.mutate(null)}
                  disabled={change.isPending}
                  className="rounded-md border border-border bg-white px-3 py-1.5 text-[13px] font-bold disabled:opacity-50"
                >
                  알림 끄기
                </button>
              )}
            </div>
          </div>
        </SheetContent>
      </Sheet>
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </>
  );
}
