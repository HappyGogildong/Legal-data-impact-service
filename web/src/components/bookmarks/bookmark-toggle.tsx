"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import {
  addBookmark,
  BOOKMARKS_KEY,
  removeBookmark,
} from "@/lib/api/bookmarks";
import { ApiError, isUnauthorized } from "@/lib/api/errors";
import { setReturnTo } from "@/lib/return-to";
import { useSession } from "@/lib/session";
import { useBookmarks } from "./use-bookmarks";

const FAILED = "북마크를 바꾸지 못했어요";

/**
 * ☆/★ 관심 법령 북마크(D72, 법령 단위) — S02·S05 법령명 줄. 상태는 북마크 목록 하나로 판단한다.
 * 익명이면 돌아올 경로를 두고 로그인으로(돌아와도 자동 추가 없음).
 */
export function BookmarkToggle({ lawId }: { lawId: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const session = useSession();
  const signedIn = session.isSuccess && session.data !== null;
  const bookmarks = useBookmarks(signedIn);
  const [message, setMessage] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const saved = bookmarks.data?.some((item) => item.lawId === lawId) ?? false;
  const toggle = useMutation({
    mutationFn: (next: boolean) =>
      next ? addBookmark(lawId) : removeBookmark(lawId),
    onError: (error) => {
      if (isUnauthorized(error)) setExpired(true);
      else
        setMessage(
          error instanceof ApiError && error.status === 409
            ? error.message
            : FAILED,
        );
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: BOOKMARKS_KEY }),
  });
  // 요청 중엔 누른 결과를 먼저 보여 주고(낙관적), 끝나면 다시 받은 목록을 따른다 — 실패면 원래대로
  const pressed = toggle.isPending ? toggle.variables : saved;

  function onClick() {
    setMessage(null);
    if (!signedIn) {
      setReturnTo(pathname);
      router.push("/login");
      return;
    }
    toggle.mutate(!pressed);
  }

  return (
    <span className="inline-flex items-center gap-1.5">
      <button
        type="button"
        onClick={onClick}
        disabled={
          session.isPending ||
          toggle.isPending ||
          (signedIn && bookmarks.isPending)
        }
        aria-pressed={pressed}
        aria-label="북마크"
        className="text-lg leading-none text-navy disabled:opacity-50"
      >
        <span aria-hidden>{pressed ? "★" : "☆"}</span>
      </button>
      {message && (
        <span role="alert" className="text-xs text-removed">
          {message}
        </span>
      )}
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </span>
  );
}
