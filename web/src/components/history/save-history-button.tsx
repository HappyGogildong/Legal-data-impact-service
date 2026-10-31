"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import { ApiError, isUnauthorized } from "@/lib/api/errors";
import { HISTORY_KEY, saveHistory } from "@/lib/api/history";
import { setReturnTo } from "@/lib/return-to";
import { useSession } from "@/lib/session";

/**
 * S02 "이력에 저장"(D73) — 서버가 세션에 보관한 결과(analysisId)로 스냅샷을 저장한다.
 * 익명이면 로그인으로(돌아와도 자동 저장 없음), 로그인인데 analysisId 가 없는 응답이면 그리지 않는다.
 * 개인정보 안내는 여기 두지 않는다 — S15 개인정보처리방침 요약(D73 ⑦).
 */
export function SaveHistoryButton({ analysisId }: { analysisId?: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const session = useSession();
  const queryClient = useQueryClient();
  const signedIn = session.isSuccess && session.data !== null;
  const [message, setMessage] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const save = useMutation({
    mutationFn: (id: string) => saveHistory(id),
    onMutate: () => setMessage(null),
    // 목록·상세 캐시를 갱신 — 다시 저장하면 같은 기록의 스냅샷이 바뀐다
    onSuccess: () => queryClient.invalidateQueries({ queryKey: HISTORY_KEY }),
    onError: (error) => {
      if (isUnauthorized(error)) setExpired(true);
      else
        setMessage(
          error instanceof ApiError && error.status === 409
            ? error.message
            : "저장하지 못했어요",
        );
    },
  });

  if (session.isPending) return null;
  if (signedIn && !analysisId) return null;
  if (save.isSuccess) {
    return (
      <p className="text-[12.5px]">
        저장했어요 ·{" "}
        <Link
          href={`/me/history/${save.data.id}`}
          className="font-bold text-navy"
        >
          이력 보기
        </Link>
      </p>
    );
  }

  function onClick() {
    if (!signedIn || !analysisId) {
      setReturnTo(pathname);
      router.push("/login");
      return;
    }
    save.mutate(analysisId);
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <button
        type="button"
        onClick={onClick}
        disabled={save.isPending}
        className="rounded-md border border-border bg-white px-3 py-1.5 text-[13px] font-bold disabled:opacity-50"
      >
        이력에 저장
      </button>
      {message && (
        <p role="alert" className="text-xs text-removed">
          {message}
        </p>
      )}
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}
